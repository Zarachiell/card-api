package com.example.api.service;

import com.example.api.config.properties.CryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CryptoServiceImplTest {

    // chaves válidas
    private static final String AES_KEY_HEX =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"; // 32 bytes (64 hex)
    private static final String HMAC_KEY_HEX =
            "a1a2a3a4a5a6a7a8a9aaabacadaeaf00"; // 16 bytes

    private CryptoProperties props;
    private CryptoServiceImpl service;

    @BeforeEach
    void setUp() {
        props = mock(CryptoProperties.class);
        when(props.aesKeyHex()).thenReturn(AES_KEY_HEX);
        when(props.hmacKeyHex()).thenReturn(HMAC_KEY_HEX);
        service = new CryptoServiceImpl(props);
    }

    @Test
    @DisplayName("AES-GCM: encryptUtf8 produz Base64(IV(12) || CT+TAG) e decripta para o original")
    void encrypt_roundTrip_ok() throws Exception {
        String plain = "hello-ç-ß-测试";
        String b64 = service.encryptUtf8(plain);

        byte[] packed = Base64.getDecoder().decode(b64);
        assertThat(packed.length).isGreaterThan(12);
        byte[] iv = slice(packed, 0, 12);
        byte[] ct = slice(packed, 12, packed.length - 12);

        var sk = new SecretKeySpec(hex(AES_KEY_HEX), "AES");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, sk, new GCMParameterSpec(128, iv));
        String out = new String(c.doFinal(ct), StandardCharsets.UTF_8);

        assertThat(out).isEqualTo(plain);
    }

    @Test
    @DisplayName("AES-GCM: mesmo texto duas vezes => IV aleatório => ciphertexts diferentes")
    void encrypt_randomIv_changesCiphertext() {
        String c1 = service.encryptUtf8("same message");
        String c2 = service.encryptUtf8("same message");
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    @DisplayName("HMAC: determinístico e em hex (64 chars)")
    void hmac_deterministic_hex() throws Exception {
        String m1 = service.macHex("abc");
        String m2 = service.macHex("abc");
        assertThat(m1).isEqualTo(m2).hasSize(64).matches("[0-9a-f]{64}");

        // valida contra implementação de referência
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hex(HMAC_KEY_HEX), "HmacSHA256"));
        String expected = toHex(mac.doFinal("abc".getBytes(StandardCharsets.UTF_8)));
        assertThat(m1).isEqualTo(expected);
    }

    @Test
    @DisplayName("HMAC: mudar a chave muda o MAC")
    void hmac_changesWithKey() {
        var props2 = mock(CryptoProperties.class);
        when(props2.aesKeyHex()).thenReturn(AES_KEY_HEX);
        when(props2.hmacKeyHex()).thenReturn("ffffffffffffffffffffffffffffffff"); // outra de 16 bytes
        var svc2 = new CryptoServiceImpl(props2);

        assertThat(service.macHex("msg")).isNotEqualTo(svc2.macHex("msg"));
    }

    @Test
    @DisplayName("Erro: AES key com tamanho inválido")
    void error_invalidAesKeyLength() {
        when(props.aesKeyHex()).thenReturn(AES_KEY_HEX.substring(0, 62)); // 31 bytes
        var bad = new CryptoServiceImpl(props);
        assertThatThrownBy(() -> bad.encryptUtf8("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("Erro: AES key hex de tamanho ímpar")
    void error_invalidHexOddLength() {
        when(props.aesKeyHex()).thenReturn(AES_KEY_HEX + "0"); // odd
        var bad = new CryptoServiceImpl(props);
        assertThatThrownBy(() -> bad.encryptUtf8("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hex odd length");
    }

    @Test
    @DisplayName("Erro: HMAC key curta demais")
    void error_hmacTooShort() {
        when(props.hmacKeyHex()).thenReturn("deadbeef"); // 4 bytes
        var bad = new CryptoServiceImpl(props);
        assertThatThrownBy(() -> bad.macHex("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    private static byte[] slice(byte[] a, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(a, off, out, 0, len);
        return out;
    }

    private static byte[] hex(String s) {
        int n = s.length();
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        return out;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}