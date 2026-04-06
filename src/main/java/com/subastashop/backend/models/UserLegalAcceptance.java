package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "UserLegalAcceptance")
public class UserLegalAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private String termsVersion;

    @Column(nullable = false)
    private LocalDateTime acceptanceTimestamp;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false, length = 500)
    private String userAgent;

    @Column(nullable = false)
    private String type; // e.g., "CONTEST_JOIN", "SELLER_REGISTRATION"
}
