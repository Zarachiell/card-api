package com.example.api.service.interfaces;

import com.example.api.DTO.CardRef;
import com.example.api.service.CardSecureServiceImpl;
import com.example.api.web.request.CardCreateRequest;

import java.util.Optional;

public interface CardSecureService {
    record PersistResult(String id, String token, String last4, boolean duplicate) {}
    PersistResult createOrGet(CardCreateRequest req, String lot, Integer seq);
    Optional<CardRef> findByPan(String rawPan);
}
