package com.example.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;

@Configuration
public class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        // pega as chaves do Keycloak via hostname do cluster (alcançável pela app)
        String jwks = "http://keycloak:8081/realms/cards/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();

        // valida o issuer exatamente como o token emite (localhost:8081)
        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer("http://localhost:8081/realms/cards");

        decoder.setJwtValidator(validator);
        return decoder;
    }
}
