package com.example.api.service.interfaces;

public interface CryptoService {
    String encryptUtf8(String plaintext);
    String macHex(String data);
}
