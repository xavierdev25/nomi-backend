package com.foodv.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranque completo del contexto con el perfil {@code test}. Necesita PostgreSQL en
 * {@code localhost:5432} y Redis en {@code localhost:6380}.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
