package com.nomi.backend.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Documentación OpenAPI de la API (Swagger UI en {@code /api/swagger-ui.html}, fuera del perfil
 * {@code prod}): descripción general, esquema de seguridad Bearer JWT, formato de error común y
 * descripción de cada grupo de endpoints.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "Bearer Auth";
    private static final String API_ERROR = "ApiError";

    /** Rutas públicas: se documentan sin requisito de token. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/refresh", "/payments/webhook");

    private static final String DESCRIPTION = """
            API REST de **Nomi**, plataforma de pedidos de comida dentro del campus universitario: \
            el estudiante pide a una tienda del campus y recibe el pedido en su aula, entre clases.

            ### Autenticación
            1. `POST /auth/login` devuelve un `accessToken` (24 h) y un `refreshToken` (7 días).
            2. Enviar `Authorization: Bearer <accessToken>` en cada petición.
            3. Ante un `401`, renovar con `POST /auth/refresh`. El refresh token **se rota en cada \
            uso**: reutilizar uno ya usado revoca todas las sesiones del usuario.

            `401` significa sin sesión válida (falta el token, expiró o fue revocado); `403` significa \
            autenticado pero sin permiso (rol u ownership).

            ### Errores
            Todas las respuestas de error usan el esquema `ApiError`. En errores de validación, \
            `fields` trae el mensaje de cada campo. La única excepción es el login inválido, que \
            responde `{"error": "Invalid credentials"}`.

            ### Límites
            Por minuto: 5 peticiones a `/ai/**`, 30 a login/registro/refresh y 100 al resto. Superarlos \
            responde `429`. Tras 5 logins fallidos, el email queda bloqueado 15 minutos.

            ### Convenciones
            - Paginación: `page` desde 0 y `size` de 1 a 200 (20 por defecto).
            - Importes en soles con 2 decimales. En un pedido, `total` es el subtotal de productos; \
            lo cobrado es `total + propina + tarifaServicio + comisionNomi`.
            - Fechas `LocalDateTime` sin zona horaria, en hora local del servidor.
            """;

    private static final List<Tag> TAGS = List.of(
            new Tag().name("Autenticación").description("Registro, login, renovación de tokens y logout."),
            new Tag().name("Usuarios").description("Perfil del usuario autenticado y administración de usuarios."),
            new Tag().name("Tiendas").description("Tiendas del campus. Cada comercio gestiona una."),
            new Tag().name("Productos").description("Catálogo de cada tienda y búsqueda."),
            new Tag().name("Aulas").description("Aulas del campus donde se entregan los pedidos."),
            new Tag().name("Órdenes").description("Ciclo de vida del pedido: PENDIENTE → PREPARANDO → LISTO_PARA_RECOGER → EN_CAMINO → ENTREGADO, o CANCELADO desde PENDIENTE."),
            new Tag().name("Pagos").description("Pago del pedido con MercadoPago y webhook de confirmación."),
            new Tag().name("Favoritos").description("Productos y tiendas favoritos."),
            new Tag().name("Calificaciones").description("Calificación de pedidos entregados."),
            new Tag().name("Inteligencia Artificial").description("Recomendaciones personalizadas generadas por el servicio de IA."),
            new Tag().name("Imágenes").description("Imágenes de productos y tiendas (Cloudinary).")
    );

    @Bean
    public OpenAPI nomiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nomi API")
                        .description(DESCRIPTION)
                        .version("1.0.0")
                        .contact(new Contact().name("Nomi Team").email("nomi@ucv.edu.pe")))
                .tags(TAGS)
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * Completa lo que las anotaciones no expresan: registra el esquema {@code ApiError} (aquí y
     * no en {@link #nomiOpenAPI()}, porque springdoc reemplaza los esquemas al generar el
     * documento), quita el candado de las rutas públicas, añade las respuestas de error comunes
     * ({@code 400} si hay cuerpo, {@code 401}/{@code 403} si requiere sesión, {@code 429} y
     * {@code 500} siempre) y asocia {@code ApiError} a todas ellas.
     */
    @Bean
    public OpenApiCustomizer commonResponsesCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(API_ERROR, apiErrorSchema());
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (PUBLIC_PATHS.contains(path)) {
                    operation.setSecurity(List.of());
                }
                ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
                if (!PUBLIC_PATHS.contains(path)) {
                    responses.putIfAbsent("401", errorResponse("Sin sesión válida: falta el token, expiró o fue revocado"));
                    responses.putIfAbsent("403", errorResponse("Autenticado, pero sin permiso sobre el recurso"));
                }
                if (operation.getRequestBody() != null) {
                    responses.putIfAbsent("400", errorResponse("Datos inválidos; en errores de validación, fields detalla cada campo"));
                }
                responses.forEach((code, response) -> {
                    if (isError(code)) {
                        response.setContent(errorContent());
                    }
                });
                responses.putIfAbsent("429", errorResponse("Demasiadas peticiones: se superó el límite por minuto"));
                responses.putIfAbsent("500", errorResponse("Error inesperado del servidor"));
                operation.setResponses(responses);
            }));
        };
    }

    private static boolean isError(String code) {
        return code.length() == 3 && (code.startsWith("4") || code.startsWith("5"));
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(errorContent());
    }

    private static Content errorContent() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR)));
    }

    @SuppressWarnings("rawtypes")
    private static Schema apiErrorSchema() {
        return new ObjectSchema()
                .description("Cuerpo de error común a toda la API.")
                .addProperty("timestamp", new StringSchema().example("2026-09-26T00:13:59.871044")
                        .description("Momento del error (hora local del servidor, sin zona)"))
                .addProperty("status", new IntegerSchema().example(400).description("Código HTTP"))
                .addProperty("error", new StringSchema().example("Solicitud inválida").description("Categoría del error"))
                .addProperty("message", new StringSchema().example("La propina excede el máximo permitido")
                        .description("Mensaje para mostrar al usuario"))
                .addProperty("fields", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .example(Map.of("password", "La contraseña debe tener entre 8 y 72 caracteres"))
                        .description("Solo en errores de validación: mensaje por campo"));
    }
}
