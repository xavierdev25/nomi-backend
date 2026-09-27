package com.nomi.backend.application.ai;

import com.nomi.backend.domain.model.ai.AiFeedback;
import com.nomi.backend.domain.model.ai.AiRecommendationRequest;
import com.nomi.backend.domain.model.ai.AiRecommendationResponse;
import com.nomi.backend.domain.model.order.OrderItem;
import com.nomi.backend.domain.model.order.OrderStatus;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.domain.model.store.Store;
import com.nomi.backend.domain.model.user.BudgetRange;
import com.nomi.backend.domain.model.user.DietaryRestriction;
import com.nomi.backend.domain.model.user.User;
import com.nomi.backend.domain.port.in.ai.GetRecommendationsUseCase;
import com.nomi.backend.domain.port.out.AiFeedbackRepositoryPort;
import com.nomi.backend.domain.port.out.AiRecommendationPort;
import com.nomi.backend.domain.port.out.OrderRepositoryPort;
import com.nomi.backend.domain.port.out.ProductRepositoryPort;
import com.nomi.backend.domain.port.out.StoreRepositoryPort;
import com.nomi.backend.domain.port.out.UserRepositoryPort;
import com.nomi.backend.domain.service.RecommendationCandidates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recomendaciones en dos pasos: el backend elige de forma determinista qué productos se pueden
 * recomendar ({@link RecommendationCandidates}) y el servicio de IA solo los ordena y explica.
 *
 * <p>El contexto que recibe el modelo son las restricciones y preferencias del perfil, los
 * nombres de los 3 productos más pedidos en los últimos 3 meses y la franja horaria (con el
 * presupuesto a la hora del almuerzo). Si no hay candidatos, o el usuario tiene una restricción
 * que no se puede comprobar, se responde sin llamar al servicio de IA con
 * {@link #NO_CANDIDATES}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetRecommendationsHandler implements GetRecommendationsUseCase {

    /** {@code generatedBy} cuando no hay ningún producto que cumpla los filtros. */
    public static final String NO_CANDIDATES = "SIN_CANDIDATOS";

    private final AiRecommendationPort aiRecommendationPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final StoreRepositoryPort storeRepositoryPort;
    private final AiFeedbackRepositoryPort aiFeedbackRepositoryPort;

    @Override
    public AiRecommendationResponse execute(GetRecommendationsCommand command) {
        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + command.userId()));

        Optional<Set<DietaryRestriction>> restrictions = DietaryRestriction.parseAll(user.getRestrictions());
        if (restrictions.isEmpty()) {
            log.warn("Usuario {} con una restricción alimentaria desconocida: sin recomendaciones", user.getId());
            return noCandidates(user.getId());
        }

        List<Long> mostOrderedProductIds = mostOrderedProductIds(user.getId());
        Set<Long> activeStoreIds = storeRepositoryPort.findAllActivas().stream()
                .map(Store::getId)
                .collect(Collectors.toSet());
        Set<Long> dislikedProductIds = aiFeedbackRepositoryPort.findDislikedByUserId(user.getId()).stream()
                .map(AiFeedback::getProductId)
                .collect(Collectors.toSet());

        List<Product> candidates = RecommendationCandidates.select(
                productRepositoryPort.findAll(),
                activeStoreIds,
                restrictions.get(),
                dislikedProductIds,
                mostOrderedProductIds);
        if (candidates.isEmpty()) {
            return noCandidates(user.getId());
        }

        // La franja horaria va primero: el servicio de IA usa solo las 10 primeras preferencias.
        List<String> preferences = new ArrayList<>(
                timeOfDayPreferences(LocalDateTime.now().getHour(), user.getBudgetRange()));
        if (user.getPreferences() != null) {
            preferences.addAll(user.getPreferences());
        }
        productRepositoryPort.findAllById(mostOrderedProductIds.stream().limit(3).toList()).stream()
                .map(Product::getNombre)
                .filter(nombre -> !preferences.contains(nombre))
                .forEach(preferences::add);

        AiRecommendationRequest request = AiRecommendationRequest.builder()
                .userId(user.getId())
                .restrictions(restrictions.get().stream().map(Enum::name).sorted().toList())
                .preferences(preferences)
                .availableProducts(candidates.stream()
                        .map(p -> new AiRecommendationRequest.ProductInfo(
                                p.getId(), p.getNombre(), p.getPrecio(), p.getCategoria().name()))
                        .toList())
                .maxRecommendations(command.maxRecommendations())
                .build();

        return aiRecommendationPort.getRecommendations(request);
    }

    /**
     * Preferencias según la hora: desayuno (6–10), almuerzo y presupuesto (11–15) o snack
     * (15–20). El presupuesto va como texto sin {@code :} porque el servicio de IA rechaza ese
     * carácter en las preferencias.
     */
    static List<String> timeOfDayPreferences(int hour, BudgetRange budgetRange) {
        if (hour >= 6 && hour < 10) {
            return List.of("desayuno");
        }
        if (hour >= 11 && hour < 15) {
            return budgetRange == null
                    ? List.of("almuerzo")
                    : List.of("almuerzo", "presupuesto " + budgetRange.name().toLowerCase(Locale.ROOT));
        }
        if (hour >= 15 && hour < 20) {
            return List.of("snack");
        }
        return List.of();
    }

    /** Productos de los últimos 10 pedidos entregados (3 meses), del más pedido al menos. */
    private List<Long> mostOrderedProductIds(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusMonths(3);
        Map<Long, Long> frequency = orderRepositoryPort.findByUserId(userId).stream()
                .filter(o -> o.getStatus() == OrderStatus.ENTREGADO)
                .filter(o -> o.getCreadoEn() != null && o.getCreadoEn().isAfter(since))
                .sorted((a, b) -> b.getCreadoEn().compareTo(a.getCreadoEn()))
                .limit(10)
                .flatMap(o -> o.getItems() != null ? o.getItems().stream() : Stream.<OrderItem>empty())
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        return frequency.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static AiRecommendationResponse noCandidates(Long userId) {
        return AiRecommendationResponse.builder()
                .userId(userId)
                .recommendations(List.of())
                .generatedBy(NO_CANDIDATES)
                .build();
    }
}
