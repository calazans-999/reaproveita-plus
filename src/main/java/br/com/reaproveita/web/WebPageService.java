package br.com.reaproveita.web;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import br.com.reaproveita.service.*;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebPageService {
    private final UserRepository users;
    private final DonationRepository donations;
    private final ReservationRepository reservations;
    private final OrganizationService organizations;
    private final DonationService donationService;
    private final RecommendationService recommendations;

    public WebPageService(UserRepository users, DonationRepository donations,
            ReservationRepository reservations, OrganizationService organizations,
            DonationService donationService, RecommendationService recommendations) {
        this.users = users;
        this.donations = donations;
        this.reservations = reservations;
        this.organizations = organizations;
        this.donationService = donationService;
        this.recommendations = recommendations;
    }

    @Transactional(readOnly = true)
    public UserRole role(Authentication authentication) {
        return current(authentication).getRole();
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> ownDonations(Authentication authentication) {
        User user = current(authentication);
        return donations.findAll().stream()
            .filter(d -> user.getRole() == UserRole.ADMIN ||
                Objects.equals(d.getOrganization().getOwner().getId(), user.getId()))
            .map(DonationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> visibleReservations(Authentication authentication) {
        User user = current(authentication);
        return reservations.findAll().stream().filter(r -> user.getRole() == UserRole.ADMIN
                || Objects.equals(r.getReceiverOrganization().getOwner().getId(), user.getId())
                || Objects.equals(r.getDonation().getOrganization().getOwner().getId(), user.getId()))
            .map(ReservationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forReceiver(long organizationId, Authentication auth) {
        return recommendations.forReceiver(organizationId, auth);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forDonation(long donationId, Authentication auth) {
        return recommendations.forDonation(donationId, auth);
    }

    public List<OrganizationResponse> organizations(Authentication authentication) {
        return organizations.list(authentication);
    }

    public List<DonationResponse> availableDonations(Authentication authentication) {
        return donationService.list(null, authentication);
    }

    private User current(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    }
}
