package com.foodv.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del backend de FoodV: API REST (contexto {@code /api}) para el sistema de
 * pedidos universitarios.
 *
 * <p>Arquitectura hexagonal: {@code domain} (modelo y puertos, sin Spring), {@code application}
 * (casos de uso) e {@code infrastructure} (web, persistencia, seguridad e integraciones).
 */
@EnableCaching
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableScheduling
@SpringBootApplication
public class BackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}
}
