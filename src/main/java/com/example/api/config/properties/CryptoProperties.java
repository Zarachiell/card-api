package com.example.api.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cards.crypto")
public record CryptoProperties(
        String aesKeyHex,   // 32 bytes (64 hex) para AES-256
        String hmacKeyHex   // 32 bytes (64 hex) para HMAC-SHA256
) {}
