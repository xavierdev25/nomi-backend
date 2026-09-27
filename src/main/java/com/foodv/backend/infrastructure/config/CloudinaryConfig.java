package com.foodv.backend.infrastructure.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de Cloudinary para el almacenamiento de imágenes.
 */
@Configuration
public class CloudinaryConfig {

    @Value("${CLOUDINARY_CLOUD_NAME:placeholder}")
    private String cloudName;

    @Value("${CLOUDINARY_API_KEY:placeholder}")
    private String apiKey;

    @Value("${CLOUDINARY_API_SECRET:placeholder}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret,
            "secure", true
        ));
    }
}
