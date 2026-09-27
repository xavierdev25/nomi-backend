package com.foodv.backend.infrastructure.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodv.backend.domain.model.payment.Payment;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.payment.CreatePaymentUseCase;
import com.foodv.backend.domain.port.in.payment.FindPaymentUseCase;
import com.foodv.backend.domain.port.in.payment.ProcessWebhookUseCase;
import com.foodv.backend.infrastructure.payment.MercadoPagoSignatureVerifier;
import com.foodv.backend.infrastructure.security.AuthenticatedUserResolver;
import com.foodv.backend.infrastructure.web.dto.payment.CreatePaymentRequest;
import com.foodv.backend.infrastructure.web.dto.payment.PaymentResponse;
import com.foodv.backend.infrastructure.web.mapper.PaymentWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Pagos con MercadoPago y su webhook.
 */
@Slf4j
@Tag(name = "Pagos")
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CreatePaymentUseCase createPaymentUseCase;
    private final FindPaymentUseCase findPaymentUseCase;
    private final ProcessWebhookUseCase processWebhookUseCase;
    private final PaymentWebMapper mapper;
    private final MercadoPagoSignatureVerifier signatureVerifier;
    private final AuthenticatedUserResolver currentUser;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase,
                             FindPaymentUseCase findPaymentUseCase,
                             ProcessWebhookUseCase processWebhookUseCase,
                             PaymentWebMapper mapper,
                             MercadoPagoSignatureVerifier signatureVerifier,
                             AuthenticatedUserResolver currentUser) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.findPaymentUseCase = findPaymentUseCase;
        this.processWebhookUseCase = processWebhookUseCase;
        this.mapper = mapper;
        this.signatureVerifier = signatureVerifier;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Crear pago MercadoPago para una orden propia")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos sobre la orden")
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        String email = currentUser.currentEmail();
        Payment payment = createPaymentUseCase.execute(mapper.toCommand(request, email));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(payment));
    }

    @Operation(summary = "Obtener pago por id (sólo dueño o ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos"),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable Long id) {
        Payment payment = findPaymentUseCase.findById(id);
        ensureOwnerOrAdmin(payment.getUserId());
        return ResponseEntity.ok(mapper.toResponse(payment));
    }

    @Operation(summary = "Obtener pago por orden (sólo dueño o ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos"),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> findByOrderId(@PathVariable Long orderId) {
        Payment payment = findPaymentUseCase.findByOrderId(orderId);
        ensureOwnerOrAdmin(payment.getUserId());
        return ResponseEntity.ok(mapper.toResponse(payment));
    }

    @Operation(summary = "Listar mis pagos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pagos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/me")
    public ResponseEntity<List<PaymentResponse>> findMine() {
        Long userId = currentUser.currentUserId();
        List<PaymentResponse> responses = findPaymentUseCase.findByUserId(userId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Listar pagos por usuario (sólo ADMIN)")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponse>> findByUserId(@PathVariable Long userId) {
        User me = currentUser.currentUserSummary();
        if (me.getRole() != UserRole.ADMIN && !me.getId().equals(userId)) {
            throw new AccessDeniedException("Sin permisos para ver pagos ajenos");
        }
        List<PaymentResponse> responses = findPaymentUseCase.findByUserId(userId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Webhook MercadoPago (verificación HMAC obligatoria)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook procesado"),
            @ApiResponse(responseCode = "401", description = "Firma inválida"),
            @ApiResponse(responseCode = "400", description = "Payload inválido")
    })
    /**
     * Webhook de MercadoPago. Público, pero solo se procesa si la firma {@code x-signature} es
     * válida. Las notificaciones {@code merchant_order} se ignoran; las de pago se procesan con
     * {@code ProcessWebhookUseCase}. Un error de procesamiento responde 500 para que MercadoPago
     * reintente.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "id", required = false) String idParam,
            HttpServletRequest request) {
        String signedDataId = dataId != null ? dataId : idParam;
        String externalId = signedDataId;
        String action = null;
        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(rawBody, new TypeReference<>() {});
            Object dataObj = payload.get("data");
            if ((externalId == null || externalId.isBlank()) && dataObj instanceof Map<?, ?> dataMap) {
                Object idObj = dataMap.get("id");
                if (idObj != null) externalId = idObj.toString();
            }
            Object actionObj = payload.get("action");
            if (actionObj != null) action = actionObj.toString();
        } catch (Exception e) {
            log.warn("Webhook MercadoPago: payload inválido. {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        if (signature == null || requestId == null) {
            log.warn("Webhook rechazado: falta firma o request-id. dataId={}", externalId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!signatureVerifier.verify(signature, requestId, signedDataId)) {
            log.warn("Webhook MercadoPago: firma inválida. requestId={}, signedDataId={}",
                    requestId, signedDataId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (externalId == null || externalId.isBlank()) {
            log.warn("Webhook MercadoPago: data.id ausente.");
            return ResponseEntity.badRequest().build();
        }

        String topic = request.getParameter("topic");
        String type = request.getParameter("type");
        if (topic == null || topic.isBlank()) {
            log.warn("Webhook MercadoPago: topic ausente.");
            return ResponseEntity.badRequest().build();
        }
        if ("merchant_order".equals(topic)) {
            log.info("Webhook merchant_order ignorado. id={}, type={}", externalId, type);
            return ResponseEntity.ok().build();
        }

        try {
            processWebhookUseCase.execute(
                    new ProcessWebhookUseCase.WebhookEvent(externalId, action, null)
            );
        } catch (Exception e) {
            log.error("Webhook MercadoPago: error procesando externalId={}", externalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    private void ensureOwnerOrAdmin(Long ownerUserId) {
        User me = currentUser.currentUserSummary();
        if (me.getRole() == UserRole.ADMIN) return;
        if (me.getId().equals(ownerUserId)) return;
        throw new AccessDeniedException("Sin permisos sobre este pago");
    }
}
