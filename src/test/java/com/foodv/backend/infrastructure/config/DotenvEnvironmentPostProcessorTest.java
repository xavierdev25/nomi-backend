package com.foodv.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Carga del {@code .env}: precedencia frente al entorno y a {@code application.yaml}, formato
 * de valores y registro del procesador en {@code spring.factories}.
 */
class DotenvEnvironmentPostProcessorTest {

    @TempDir
    Path dir;

    @Test
    void entorno_real_gana_al_dotenv_y_dotenv_gana_a_application_yaml() throws IOException {
        Path dotenv = dir.resolve(".env");
        Files.writeString(dotenv, """
                # comentario
                COMPARTIDA=desde-dotenv
                SOLO_DOTENV=desde-dotenv
                """);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("COMPARTIDA", "desde-entorno")));
        environment.getPropertySources().addLast(
                new MapPropertySource("applicationConfig", Map.of("SOLO_DOTENV", "desde-yaml")));

        new DotenvEnvironmentPostProcessor(dotenv).postProcessEnvironment(environment, new SpringApplication());

        assertEquals("desde-entorno", environment.getProperty("COMPARTIDA"));
        assertEquals("desde-dotenv", environment.getProperty("SOLO_DOTENV"));
    }

    @Test
    void valores_entre_comillas_son_literales_y_sin_comillas_admiten_comentario() throws IOException {
        Path dotenv = dir.resolve(".env");
        Files.writeString(dotenv, """
                CON_COMILLAS="clave # con almohadilla"
                SIMPLES='otra=clave'
                EN_LINEA=valor # comentario
                """);
        StandardEnvironment environment = new StandardEnvironment();

        new DotenvEnvironmentPostProcessor(dotenv).postProcessEnvironment(environment, new SpringApplication());

        assertEquals("clave # con almohadilla", environment.getProperty("CON_COMILLAS"));
        assertEquals("otra=clave", environment.getProperty("SIMPLES"));
        assertEquals("valor", environment.getProperty("EN_LINEA"));
    }

    @Test
    void sin_archivo_dotenv_no_agrega_nada() {
        StandardEnvironment environment = new StandardEnvironment();

        new DotenvEnvironmentPostProcessor(dir.resolve("no-existe.env"))
                .postProcessEnvironment(environment, new SpringApplication());

        assertFalse(environment.getPropertySources()
                .contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    /**
     * Spring Boot solo descubre los {@code EnvironmentPostProcessor} por {@code META-INF/spring.factories}:
     * sin este registro el {@code .env} nunca se carga.
     */
    @Test
    void esta_registrado_en_spring_factories() throws IOException {
        String key = "org.springframework.boot.EnvironmentPostProcessor";
        boolean registered = false;
        for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
            Properties factories = new Properties();
            try (InputStream in = url.openStream()) {
                factories.load(in);
            }
            String value = factories.getProperty(key, "");
            registered |= value.contains(DotenvEnvironmentPostProcessor.class.getName());
        }
        assertTrue(registered, "DotenvEnvironmentPostProcessor no está registrado bajo " + key);
    }
}
