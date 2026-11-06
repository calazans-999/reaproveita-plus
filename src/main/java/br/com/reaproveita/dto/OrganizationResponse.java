package br.com.reaproveita.dto;

import br.com.reaproveita.model.Organization;
import br.com.reaproveita.model.OrganizationType;
import java.math.BigDecimal;

public record OrganizationResponse(Long id, Long ownerId, String name,
    OrganizationType type, String city, BigDecimal latitude,
    BigDecimal longitude, boolean verified) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getOwner().getId(),
            organization.getName(), organization.getType(), organization.getCity(),
            organization.getLatitude(), organization.getLongitude(), organization.isVerified());
    }
}
