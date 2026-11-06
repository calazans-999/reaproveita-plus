package br.com.reaproveita.service;

import br.com.reaproveita.dto.RecommendationResponse;
import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    // MVP: distancia em linha reta; nao equivale a percurso real de retirada.
    private static final double MAX_DISTANCE_KM = 50.0;
    private final DonationRepository donations;
    private final ReceiverProfileRepository profiles;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public RecommendationService(DonationRepository donations, ReceiverProfileRepository profiles,
                                 OrganizationRepository organizations, UserRepository users) {
        this.donations = donations;
        this.profiles = profiles;
        this.organizations = organizations;
        this.users = users;
    }

    private User current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        return users.findByEmailIgnoreCase(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
    }

    private static boolean eligibleDonation(Donation donation, OffsetDateTime now) {
        return donation.getStatus() == DonationStatus.AVAILABLE
            && donation.isSanitaryDeclaration() && donation.getExpiresAt().isAfter(now);
    }

    private static double distanceKm(Organization first, Organization second) {
        double lat1 = Math.toRadians(first.getLatitude().doubleValue());
        double lat2 = Math.toRadians(second.getLatitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(second.getLongitude().doubleValue()
            - first.getLongitude().doubleValue());
        double h = Math.pow(Math.sin(deltaLat / 2), 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0.0, 1.0 - h)));
    }

    private Optional<RecommendationResponse> score(Donation donation, ReceiverProfile profile, OffsetDateTime now) {
        Organization donor = donation.getOrganization();
        Organization receiver = profile.getOrganization();
        if (!eligibleDonation(donation, now)
                || receiver.getType() != OrganizationType.RECEIVER
                || !profile.isAvailableForPickup()
                || !profile.getAcceptedCategories().contains(donation.getCategory())
                || profile.getCapacityKg().compareTo(donation.getQuantityKg()) < 0
                || donor.getId().equals(receiver.getId())
                || donor.getLatitude() == null || donor.getLongitude() == null
                || receiver.getLatitude() == null || receiver.getLongitude() == null) {
            return Optional.empty();
        }
        double km = distanceKm(donor, receiver);
        if (km > MAX_DISTANCE_KM) return Optional.empty();

        double proximity = 100.0 * (1.0 - km / MAX_DISTANCE_KM);
        double compatibility = 100.0; // categoria aceita e capacidade suficiente
        double availability = 100.0; // receptor declarou disponibilidade
        long hours = Duration.between(now, donation.getExpiresAt()).toHours();
        double urgency = hours < 6 ? 100.0 : hours < 24 ? 75.0 : hours < 48 ? 50.0 : 25.0;
        int points = (int) Math.round(0.35 * proximity + 0.30 * compatibility
            + 0.20 * urgency + 0.15 * availability);

        String reason = "Categoria aceita; capacidade suficiente; retirada disponivel; "
            + "distancia aproximada de " + BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP)
            + " km em linha reta; prazo restante: " + Math.max(0, hours) + " h.";
        return Optional.of(new RecommendationResponse(donation.getId(), donation.getTitle(),
            receiver.getId(), receiver.getName(), points,
            BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP), reason, donation.getExpiresAt()));
    }

    private static List<RecommendationResponse> ordered(List<RecommendationResponse> items) {
        return items.stream().sorted(Comparator.comparingInt(RecommendationResponse::score)
            .reversed().thenComparing(RecommendationResponse::receiverOrganizationId)
            .thenComparing(RecommendationResponse::donationId)).limit(20).toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forDonation(Long donationId, Authentication auth) {
        User user = current(auth);
        Donation donation = donations.findById(donationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada"));
        if (user.getRole() != UserRole.ADMIN
                && !donation.getOrganization().getOwner().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doacao nao encontrada");
        OffsetDateTime now = OffsetDateTime.now();
        if (!eligibleDonation(donation, now)) return List.of();
        return ordered(profiles.findAll().stream().map(p -> score(donation, p, now))
            .flatMap(Optional::stream).toList());
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forReceiver(Long organizationId, Authentication auth) {
        User user = current(auth);
        Organization receiver = organizations.findById(organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada"));
        if (user.getRole() != UserRole.ADMIN && !receiver.getOwner().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada");
        if (receiver.getType() != OrganizationType.RECEIVER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizacao nao e receptora");
        Optional<ReceiverProfile> profile = profiles.findById(organizationId);
        if (profile.isEmpty()) return List.of();
        OffsetDateTime now = OffsetDateTime.now();
        return ordered(donations.findByStatusOrderByExpiresAtAsc(DonationStatus.AVAILABLE)
            .stream().map(d -> score(d, profile.get(), now)).flatMap(Optional::stream).toList());
    }
}
