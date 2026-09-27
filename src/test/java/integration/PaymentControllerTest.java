package integration;

import com.foodv.backend.domain.port.in.payment.CreatePaymentUseCase;
import com.foodv.backend.domain.port.in.payment.FindPaymentUseCase;
import com.foodv.backend.domain.port.in.payment.ProcessWebhookUseCase;
import com.foodv.backend.infrastructure.payment.MercadoPagoSignatureVerifier;
import com.foodv.backend.infrastructure.security.AuthenticatedUserResolver;
import com.foodv.backend.infrastructure.web.controller.GlobalExceptionHandler;
import com.foodv.backend.infrastructure.web.controller.PaymentController;
import com.foodv.backend.infrastructure.web.mapper.PaymentWebMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook de MercadoPago: firma HMAC válida, firma inválida y notificación sin tema.
 */
@SpringBootTest(
        classes = {
                PaymentController.class,
                GlobalExceptionHandler.class,
                MercadoPagoSignatureVerifier.class,
                PaymentControllerTest.TestBeans.class
        },
        properties = "MERCADOPAGO_WEBHOOK_SECRET=test-webhook-secret"
)
class PaymentControllerTest {

    private static final String SECRET = "test-webhook-secret";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext webApplicationContext;

    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void webhook_valid_hmac_returns_200() throws Exception {
        String dataId = "123";
        long ts = System.currentTimeMillis() / 1000L;

        mockMvc.perform(post("/payments/webhook")
                        .queryParam("topic", "payment")
                        .queryParam("data.id", dataId)
                        .header("x-request-id", "request-1")
                        .header("x-signature", signature(dataId, "request-1", ts))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(dataId)))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_invalid_signature_returns_403() throws Exception {
        mockMvc.perform(post("/payments/webhook")
                        .queryParam("topic", "payment")
                        .queryParam("data.id", "123")
                        .header("x-request-id", "request-1")
                        .header("x-signature", "ts=1,v1=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhook_missing_topic_returns_400() throws Exception {
        String dataId = "123";
        long ts = System.currentTimeMillis() / 1000L;

        mockMvc.perform(post("/payments/webhook")
                        .queryParam("data.id", dataId)
                        .header("x-request-id", "request-1")
                        .header("x-signature", signature(dataId, "request-1", ts))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(dataId)))
                .andExpect(status().isBadRequest());
    }

    private String body(String dataId) {
        return "{\"action\":\"payment.updated\",\"data\":{\"id\":\"" + dataId + "\"}}";
    }

    private String signature(String dataId, String requestId, long ts) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return "ts=" + ts + ",v1=" + hex;
    }

    @TestConfiguration
    static class TestBeans {
        @Bean CreatePaymentUseCase createPaymentUseCase() { return Mockito.mock(CreatePaymentUseCase.class); }
        @Bean FindPaymentUseCase findPaymentUseCase() { return Mockito.mock(FindPaymentUseCase.class); }
        @Bean ProcessWebhookUseCase processWebhookUseCase() { return Mockito.mock(ProcessWebhookUseCase.class); }
        @Bean PaymentWebMapper paymentWebMapper() { return Mockito.mock(PaymentWebMapper.class); }
        @Bean AuthenticatedUserResolver authenticatedUserResolver() { return Mockito.mock(AuthenticatedUserResolver.class); }
    }
}
