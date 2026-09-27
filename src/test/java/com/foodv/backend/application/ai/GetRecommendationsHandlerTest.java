package com.foodv.backend.application.ai;

import com.foodv.backend.domain.model.ai.AiRecommendationRequest;
import com.foodv.backend.domain.model.ai.AiRecommendationResponse;
import com.foodv.backend.domain.model.product.DietaryTag;
import com.foodv.backend.domain.model.product.Product;
import com.foodv.backend.domain.model.product.ProductCategory;
import com.foodv.backend.domain.model.store.Store;
import com.foodv.backend.domain.model.user.BudgetRange;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.ai.GetRecommendationsUseCase.GetRecommendationsCommand;
import com.foodv.backend.domain.port.out.AiFeedbackRepositoryPort;
import com.foodv.backend.domain.port.out.AiRecommendationPort;
import com.foodv.backend.domain.port.out.OrderRepositoryPort;
import com.foodv.backend.domain.port.out.ProductRepositoryPort;
import com.foodv.backend.domain.port.out.StoreRepositoryPort;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Recomendaciones: el backend filtra los candidatos antes de llamar al servicio de IA.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetRecommendationsHandler - Candidatos y contexto para la IA")
class GetRecommendationsHandlerTest {

    @Mock private AiRecommendationPort aiRecommendationPort;
    @Mock private ProductRepositoryPort productRepositoryPort;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private OrderRepositoryPort orderRepositoryPort;
    @Mock private StoreRepositoryPort storeRepositoryPort;
    @Mock private AiFeedbackRepositoryPort aiFeedbackRepositoryPort;

    @InjectMocks private GetRecommendationsHandler handler;

    private static User student(List<String> restrictions) {
        return User.builder()
                .id(5L).email("ana@test.local").role(UserRole.ESTUDIANTE).activo(true)
                .restrictions(restrictions).preferences(List.of()).budgetRange(BudgetRange.MEDIO)
                .build();
    }

    private static Product product(long id, String nombre, Set<DietaryTag> tags) {
        return Product.builder()
                .id(id).nombre(nombre).precio(BigDecimal.TEN).stock(5)
                .categoria(ProductCategory.COMIDA).storeId(1L).activo(true).disponible(true)
                .etiquetasDieteticas(tags)
                .build();
    }

    private void givenCatalog(User user, List<Product> catalog) {
        when(userRepositoryPort.findById(5L)).thenReturn(Optional.of(user));
        when(orderRepositoryPort.findByUserId(5L)).thenReturn(List.of());
        when(storeRepositoryPort.findAllActivas()).thenReturn(List.of(Store.builder().id(1L).activo(true).build()));
        when(aiFeedbackRepositoryPort.findDislikedByUserId(5L)).thenReturn(List.of());
        when(productRepositoryPort.findAll()).thenReturn(catalog);
    }

    @Test
    void vegetarian_student_only_sends_products_tagged_as_suitable() {
        givenCatalog(student(List.of("VEGETARIANO")), List.of(
                product(2, "Aji de Gallina", Set.of()),
                product(4, "Chicha Morada", Set.of(DietaryTag.VEGANO)),
                product(7, "Papa a la Huancaina", Set.of(DietaryTag.VEGETARIANO))));
        when(productRepositoryPort.findAllById(List.of())).thenReturn(List.of());
        when(aiRecommendationPort.getRecommendations(any())).thenReturn(
                AiRecommendationResponse.builder().userId(5L).recommendations(List.of()).generatedBy("phi3").build());

        handler.execute(new GetRecommendationsCommand(5L, 3));

        ArgumentCaptor<AiRecommendationRequest> sent = ArgumentCaptor.forClass(AiRecommendationRequest.class);
        verify(aiRecommendationPort).getRecommendations(sent.capture());
        assertEquals(List.of(4L, 7L),
                sent.getValue().getAvailableProducts().stream().map(AiRecommendationRequest.ProductInfo::id).toList());
        assertEquals(List.of("VEGETARIANO"), sent.getValue().getRestrictions());
    }

    @Test
    void without_suitable_products_the_ai_service_is_not_called() {
        givenCatalog(student(List.of("VEGANO")), List.of(product(2, "Aji de Gallina", Set.of())));

        AiRecommendationResponse response = handler.execute(new GetRecommendationsCommand(5L, 3));

        assertEquals(GetRecommendationsHandler.NO_CANDIDATES, response.getGeneratedBy());
        assertTrue(response.getRecommendations().isEmpty());
        verifyNoInteractions(aiRecommendationPort);
    }

    @Test
    void an_unknown_restriction_blocks_recommendations_instead_of_being_ignored() {
        when(userRepositoryPort.findById(5L)).thenReturn(Optional.of(student(List.of("SIN_MARISCOS"))));

        AiRecommendationResponse response = handler.execute(new GetRecommendationsCommand(5L, 3));

        assertEquals(GetRecommendationsHandler.NO_CANDIDATES, response.getGeneratedBy());
        verifyNoInteractions(aiRecommendationPort, productRepositoryPort);
    }

    @Test
    void lunch_preferences_include_the_budget_without_a_colon() {
        assertEquals(List.of("almuerzo", "presupuesto medio"),
                GetRecommendationsHandler.timeOfDayPreferences(12, BudgetRange.MEDIO));
        assertEquals(List.of("desayuno"), GetRecommendationsHandler.timeOfDayPreferences(8, BudgetRange.ALTO));
        assertEquals(List.of("snack"), GetRecommendationsHandler.timeOfDayPreferences(16, null));
        assertEquals(List.of(), GetRecommendationsHandler.timeOfDayPreferences(22, BudgetRange.BAJO));
    }
}
