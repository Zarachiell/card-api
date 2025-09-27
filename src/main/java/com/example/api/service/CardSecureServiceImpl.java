package com.example.api.service;

import com.example.api.DTO.CardRef;
import com.example.api.entity.CardToken;
import com.example.api.repository.CardTokenRepository;
import com.example.api.service.interfaces.CardSecureService;
import com.example.api.service.interfaces.CryptoService;
import com.example.api.util.PanService;
import com.example.api.util.TokenGenerator;
import com.example.api.web.request.CardCreateRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardSecureServiceImpl implements CardSecureService {

    private final CardTokenRepository repo;
    private final CryptoService crypto;
    private final PanService pan;
    private final TokenGenerator tokens;

    @Override @Transactional
    public PersistResult createOrGet(CardCreateRequest req, String lot, Integer seq) {
        String panNorm = pan.normalize(req.cardNumber());
        String hmac    = crypto.macHex(panNorm);

        return repo.findByPanHmacHex(hmac)
                .map(e -> new PersistResult(e.getId().toString(), e.getToken(), e.getLast4(), true))
                .orElseGet(() -> saveNew(req, panNorm, hmac));
    }

    public Optional<CardRef> findByPan(String rawPan) {
        String norm = pan.normalize(rawPan);          // remove não-dígitos, valida len (e Luhn se ligado)
        String hmac = crypto.macHex(norm);            // HMAC determinístico do PAN
        return repo.findByPanHmacHex(hmac)
                .map(e -> new CardRef(e.getId().toString(), e.getToken(), e.getLast4()));
    }


    private PersistResult saveNew(CardCreateRequest req, String panNorm, String hmac) {
        var entity = CardToken.builder()
                .id(tokens.newId())
                .token(tokens.newToken())
                .panHmacHex(hmac)
                .panEnc(crypto.encryptUtf8(panNorm))
                .bin(pan.bin(panNorm))
                .last4(pan.last4(panNorm))
                .brand(req.brand())
                .expiryMonth(req.expiryMonth())
                .expiryYear(req.expiryYear())
                .build();
        try {
            repo.saveAndFlush(entity);
            return new PersistResult(entity.getId().toString(), entity.getToken(), entity.getLast4(), false);
        } catch (DataIntegrityViolationException dup) {
            // corrida entre threads/instâncias: retorna o já existente
            var e = repo.findByPanHmacHex(hmac).orElseThrow();
            return new PersistResult(e.getId().toString(), e.getToken(), e.getLast4(), true);
        }
    }
}