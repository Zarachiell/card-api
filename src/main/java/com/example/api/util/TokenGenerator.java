package com.example.api.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class TokenGenerator {
    private final SecureRandom rnd = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    public UUID newId() { return UUID.randomUUID(); }
    public String newToken() { byte[] b=new byte[12]; rnd.nextBytes(b); return "tok_" + HEX.formatHex(b); }
}