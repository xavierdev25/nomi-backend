package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodv.backend.domain.exception.AuthenticationFailedException;
import com.foodv.backend.domain.model.user.UserRole;
import com.foodv.backend.domain.port.in.auth.LoginUseCase;
import com.foodv.backend.domain.port.in.auth.LogoutUseCase;
import com.foodv.backend.domain.port.in.auth.RefreshTokenUseCase;
import com.foodv.backend.domain.port.in.auth.RegisterUseCase;
import com.foodv.backend.domain.port.out.TokenBlacklistPort;
import com.foodv.backend.domain.port.out.TokenServicePort;
import com.foodv.backend.infrastructure.config.JwtConfig;
import com.foodv.backend.infrastructure.config.SecurityConfig;
import com.foodv.backend.infrastructure.security.JwtAuthenticationFilter;
import com.foodv.backend.infrastructure.security.JwtTokenServiceAdapter;
import com.foodv.backend.infrastructure.web.controller.AuthController;
import com.foodv.backend.infrastructure.web.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de autenticación con la cadena de seguridad real y los casos de uso simulados.
 */
@SpringBootTest(
        classes = {
                AuthController.class,
                GlobalExceptionHandler.class,
                SecurityConfig.class,
                JwtAuthenticationFilter.class,
                JwtTokenServiceAdapter.class,
                AuthControllerTest.TestBeans.class
        },
        properties = {
                "jwt.secret=test_jwt_secret_placeholder_minimum_32_bytes_long_value",
                "jwt.expiration=86400000",
                "jwt.refresh-expiration=604800000",
                "jwt.issuer=foodv-backend-test"
        }
)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
})
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private LoginUseCase loginUseCase;
    @Autowired private TokenServicePort tokenServicePort;

    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void register_valid_body_returns_201() throws Exception {
        Map<String, Object> body = validRegisterBody();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_unknown_restriction_returns_400() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(validRegisterBody());
        body.put("restrictions", List.of("VEGETARIANO", "SIN_MARISCOS"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_valid_credentials_returns_200() throws Exception {
        when(loginUseCase.execute(any())).thenReturn(
                new LoginUseCase.LoginResult("access-token", "refresh-token", "Bearer", 86400000L)
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "xavier@foodv.com",
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void login_wrong_password_returns_401() throws Exception {
        doThrow(new AuthenticationFailedException("Credenciales inválidas"))
                .when(loginUseCase).execute(any());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "xavier@foodv.com",
                                "password", "Wrong123"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_valid_jwt_returns_200() throws Exception {
        String token = tokenServicePort.generateAccessToken(1L, "xavier@foodv.com", UserRole.ESTUDIANTE);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "refresh-token"))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> validRegisterBody() {
        return Map.of(
                "nombres", "Xavier",
                "apellidos", "David",
                "email", "xavier@foodv.com",
                "password", "Password123",
                "telefono", "999999999",
                "role", "ESTUDIANTE",
                "preferences", List.of("pollo"),
                "restrictions", List.of(),
                "budgetRange", "MEDIO",
                "cuisineTypes", List.of("criolla")
        );
    }

    @TestConfiguration
    static class TestBeans {
        @Bean RegisterUseCase registerUseCase() { return Mockito.mock(RegisterUseCase.class); }
        @Bean LoginUseCase loginUseCase() { return Mockito.mock(LoginUseCase.class); }
        @Bean RefreshTokenUseCase refreshTokenUseCase() { return Mockito.mock(RefreshTokenUseCase.class); }
        @Bean LogoutUseCase logoutUseCase() { return Mockito.mock(LogoutUseCase.class); }
        @Bean TokenBlacklistPort tokenBlacklistPort() { return Mockito.mock(TokenBlacklistPort.class); }

        @Bean
        JwtConfig jwtConfig() {
            JwtConfig jwtConfig = new JwtConfig();
            jwtConfig.setSecret("test_jwt_secret_placeholder_minimum_32_bytes_long_value");
            jwtConfig.setExpiration(86_400_000L);
            jwtConfig.setRefreshExpiration(604_800_000L);
            jwtConfig.setIssuer("foodv-backend-test");
            return jwtConfig;
        }
    }
}
