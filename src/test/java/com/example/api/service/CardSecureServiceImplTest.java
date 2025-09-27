package com.example.api.service;

import com.example.api.entity.CardToken;
import com.example.api.repository.CardTokenRepository;
import com.example.api.service.interfaces.CryptoService;
import com.example.api.util.PanService;
import com.example.api.util.TokenGenerator;
import com.example.api.web.request.CardCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CardSecureServiceImplTest {

    private CardTokenRepository repo;
    private CryptoService crypto;
    private PanService pan;
    private TokenGenerator tokens;

    private CardSecureServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(CardTokenRepository.class);
        crypto = mock(CryptoService.class);
        pan = mock(PanService.class);
        tokens = mock(TokenGenerator.class);
        service = new CardSecureServiceImpl(repo, crypto, pan, tokens);
    }

    @Test
    @DisplayName("createOrGet: novo cartão -> salva criptografado e retorna duplicate=false")
    void createOrGet_new_savesEncrypted() {
        var req = new CardCreateRequest("4456 8979 9999 9999", "VISA", 12, 2099, null, null);
        var norm = "4456897999999999";
        var hmac = "HMAC123";
        var id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeffffffff".replace('f','f'));
        var tok = "tok_abcd";

        when(pan.normalize(req.cardNumber())).thenReturn(norm);
        when(crypto.macHex(norm)).thenReturn(hmac);
        when(repo.findByPanHmacHex(hmac)).thenReturn(Optional.empty());
        when(tokens.newId()).thenReturn(id);
        when(tokens.newToken()).thenReturn(tok);
        when(crypto.encryptUtf8(norm)).thenReturn("ENC(norm)");
        when(pan.bin(norm)).thenReturn("445689");
        when(pan.last4(norm)).thenReturn("9999");
        // devolve a própria entidade passada no save
        when(repo.saveAndFlush(any(CardToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.createOrGet(req, "LOTE0001", 1);

        assertThat(res.duplicate()).isFalse();
        assertThat(res.id()).isEqualTo(id.toString());
        assertThat(res.token()).isEqualTo(tok);
        assertThat(res.last4()).isEqualTo("9999");

        var captor = ArgumentCaptor.forClass(CardToken.class);
        verify(repo).saveAndFlush(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getPanHmacHex()).isEqualTo(hmac);
        assertThat(saved.getPanEnc()).isEqualTo("ENC(norm)");
        assertThat(saved.getBin()).isEqualTo("445689");
        assertThat(saved.getLast4()).isEqualTo("9999");
        assertThat(saved.getExpiryMonth()).isEqualTo(12);
        assertThat(saved.getExpiryYear()).isEqualTo(2099);

        verify(repo, times(1)).findByPanHmacHex(hmac);
        verify(crypto).encryptUtf8(norm);
    }

    @Test
    @DisplayName("createOrGet: cartão já existe -> não salva e retorna duplicate=true")
    void createOrGet_duplicate_shortCircuits() {
        var req = new CardCreateRequest("4111111111111111", "VISA", 1, 2099, null, null);
        var norm = "4111111111111111";
        var hmac = "HMACaaa";
        var existing = CardToken.builder()
                .id(UUID.fromString("11111111-2222-3333-4444-555555555555"))
                .token("tok_existente")
                .last4("1111")
                .panHmacHex(hmac)
                .build();

        when(pan.normalize(req.cardNumber())).thenReturn(norm);
        when(crypto.macHex(norm)).thenReturn(hmac);
        when(repo.findByPanHmacHex(hmac)).thenReturn(Optional.of(existing));

        var res = service.createOrGet(req, "L", 1);

        assertThat(res.duplicate()).isTrue();
        assertThat(res.id()).isEqualTo(existing.getId().toString());
        assertThat(res.token()).isEqualTo("tok_existente");
        assertThat(res.last4()).isEqualTo("1111");

        verify(repo, never()).saveAndFlush(any());
        verify(crypto, never()).encryptUtf8(any());
    }

    @Test
    @DisplayName("createOrGet: corrida (unique) -> save lança, busca e retorna registro existente (duplicate=true)")
    void createOrGet_raceCondition_uniqueConstraint() {
        var req = new CardCreateRequest("5555555555554444", "MC", 10, 2099, null, null);
        var norm = "5555555555554444";
        var hmac = "HMACrc";
        var existing = CardToken.builder()
                .id(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000000"))
                .token("tok_dup")
                .last4("4444")
                .panHmacHex(hmac)
                .build();

        when(pan.normalize(req.cardNumber())).thenReturn(norm);
        when(crypto.macHex(norm)).thenReturn(hmac);
        when(repo.findByPanHmacHex(hmac)).thenReturn(Optional.empty(), Optional.of(existing)); // 1ª vez vazio, 2ª depois da exceção
        when(tokens.newId()).thenReturn(UUID.randomUUID());
        when(tokens.newToken()).thenReturn("tok_new");
        when(crypto.encryptUtf8(norm)).thenReturn("ENC");
        when(pan.bin(norm)).thenReturn("555555");
        when(pan.last4(norm)).thenReturn("4444");
        when(repo.saveAndFlush(any(CardToken.class))).thenThrow(new DataIntegrityViolationException("dup"));

        var res = service.createOrGet(req, "L", 1);

        assertThat(res.duplicate()).isTrue();
        assertThat(res.id()).isEqualTo(existing.getId().toString());
        assertThat(res.token()).isEqualTo("tok_dup");
        assertThat(res.last4()).isEqualTo("4444");

        verify(repo, times(2)).findByPanHmacHex(hmac);
    }

    @Test
    @DisplayName("findByPan: existente -> retorna id/token/last4")
    void findByPan_present() {
        var raw = "  378282246310005 ";
        var norm = "378282246310005";
        var hmac = "HM";
        var entity = CardToken.builder()
                .id(UUID.fromString("99999999-0000-0000-0000-000000000000"))
                .token("tok_xyz")
                .last4("0005")
                .panHmacHex(hmac)
                .build();

        when(pan.normalize(raw)).thenReturn(norm);
        when(crypto.macHex(norm)).thenReturn(hmac);
        when(repo.findByPanHmacHex(hmac)).thenReturn(Optional.of(entity));

        var opt = service.findByPan(raw);

        assertThat(opt).isPresent();
        assertThat(opt.get().id()).isEqualTo("99999999-0000-0000-0000-000000000000");
        assertThat(opt.get().token()).isEqualTo("tok_xyz");
        assertThat(opt.get().last4()).isEqualTo("0005");
    }

    @Test
    @DisplayName("findByPan: ausente -> Optional.empty()")
    void findByPan_absent() {
        when(pan.normalize("123")).thenReturn("123");
        when(crypto.macHex("123")).thenReturn("H");
        when(repo.findByPanHmacHex("H")).thenReturn(Optional.empty());

        assertThat(service.findByPan("123")).isEmpty();
    }

    @Test
    @DisplayName("createOrGet: erro de normalização do PAN -> propaga IllegalArgumentException")
    void createOrGet_panNormalizeError() {
        var req = new CardCreateRequest("xx", "VISA", 1, 2099, null, null);
        when(pan.normalize("xx")).thenThrow(new IllegalArgumentException("invalid_pan_length"));

        assertThatThrownBy(() -> service.createOrGet(req, "L", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid_pan_length");

        verifyNoInteractions(repo);
    }
}