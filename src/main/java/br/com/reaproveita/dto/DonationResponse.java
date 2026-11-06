package br.com.reaproveita.dto;

import br.com.reaproveita.model.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DonationResponse(
    Long id, Long organizationId, String organizationName, String city,
    String title, FoodCategory category, BigDecimal quantityKg,
    OffsetDateTime expiresAt, String storageCondition,
    DonationStatus status, OffsetDateTime createdAt
) {
    public static DonationResponse from(Donation d) {
        var o = d.getOrganization();
        return new DonationResponse(d.getId(), o.getId(), o.getName(), o.getCity(),
            d.getTitle(), d.getCategory(), d.getQuantityKg(), d.getExpiresAt(),
            d.getStorageCondition(), d.getStatus(), d.getCreatedAt());
    }
}
