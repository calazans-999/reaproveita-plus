package br.com.reaproveita.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RecommendationResponse(
    Long donationId,
    String donationTitle,
    Long receiverOrganizationId,
    String receiverName,
    int score,
    BigDecimal distanceKm,
    String explanation,
    OffsetDateTime expiresAt
) { }
