// src/main/java/com/example/api/cards/CardToken.java
package com.example.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="card_tokens",
        indexes = {@Index(name="idx_expiry", columnList="expiryYear,expiryMonth"),
                @Index(name="idx_bin",    columnList="bin")}
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@ToString(exclude = {"panEnc"})
public class CardToken {
    @Id @Column(columnDefinition="BINARY(16)") private UUID id;
    @Column(nullable=false, unique=true, length=32) private String token;
    @Column(nullable=false, unique=true, length=64) private String panHmacHex;
    @Lob @Column(nullable=false) private String panEnc;
    @Column(nullable=false, length=8)  private String bin;
    @Column(nullable=false, length=4)  private String last4;
    private String brand;
    @Column(nullable=false) private int expiryMonth;
    @Column(nullable=false) private int expiryYear;
    @CreationTimestamp @Column(updatable=false) private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
