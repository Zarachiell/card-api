package com.example.api.repository;

import com.example.api.entity.CardToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardTokenRepository extends JpaRepository<CardToken, UUID> {
    Optional<CardToken> findByPanHmacHex(String panHmacHex);
}
