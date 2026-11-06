package br.com.reaproveita.dto;

import br.com.reaproveita.model.FoodCategory;
import br.com.reaproveita.model.ReceiverProfile;
import java.math.BigDecimal;
import java.util.Set;

public record ReceiverProfileResponse(
    Long organizationId,
    BigDecimal capacityKg,
    boolean availableForPickup,
    Set<FoodCategory> acceptedCategories
) {
    public static ReceiverProfileResponse from(ReceiverProfile profile) {
        return new ReceiverProfileResponse(profile.getOrganizationId(), profile.getCapacityKg(),
            profile.isAvailableForPickup(), profile.getAcceptedCategories());
    }
}
