package com.foodv.backend.infrastructure.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Carga el archivo {@code .env} del directorio de trabajo como fuente de propiedades: por
 * debajo de las variables de entorno reales, las system properties y los argumentos, pero por
 * encima de {@code application.yaml}.
 *
 * <p>Está registrado en {@code META-INF/spring.factories}: Spring Boot solo descubre los
 * {@code EnvironmentPostProcessor} por ahí. Corre antes que
 * {@code ConfigDataEnvironmentPostProcessor} para que placeholders como
 * {@code ${SPRING_PROFILES_ACTIVE}} ya lo vean al resolver los perfiles.
 *
 * <p>Formato: {@code CLAVE=valor}; líneas vacías y comentarios {@code #} se ignoran; un valor
 * entre comillas es literal ({@code #} incluido); sin comillas, {@code " #"} inicia un
 * comentario en línea.
 */
@Order(ConfigDataEnvironmentPostProcessor.ORDER - 1)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    private final Path dotenvPath;

    public DotenvEnvironmentPostProcessor() {
        this(Path.of(".env"));
    }

    DotenvEnvironmentPostProcessor(Path dotenvPath) {
        this.dotenvPath = dotenvPath;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = loadDotenv();
        if (properties.isEmpty()) return;

        MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            sources.addLast(source);
        }
    }

    private Map<String, Object> loadDotenv() {
        Map<String, Object> properties = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(dotenvPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx < 0) continue;
                String key = trimmed.substring(0, idx).trim();
                if (key.isEmpty()) continue;
                properties.put(key, parseValue(trimmed.substring(idx + 1).trim()));
            }
        } catch (IOException e) {
            // Sin .env (o ilegible): la configuración sale solo del entorno y de application.yaml.
        }
        return properties;
    }

    private static String parseValue(String raw) {
        boolean quoted = raw.length() >= 2
                && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")));
        if (quoted) {
            return raw.substring(1, raw.length() - 1);
        }
        int hash = raw.indexOf(" #");
        return hash >= 0 ? raw.substring(0, hash).trim() : raw;
    }
}
