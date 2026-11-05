package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CorePersistenceTests {
    @Autowired private UserRepository users;
    @Autowired private OrganizationRepository organizations;
    @Autowired private DonationRepository donations;
    @Autowired private ReservationRepository reservations;

    private User user(String email, UserRole role) {
        return users.saveAndFlush(new User("Pessoa Teste", email, "hash-ficticio", role));
    }

    private Organization organization(User owner, String name, OrganizationType type) {
        return organizations.saveAndFlush(new Organization(owner, name, type, "Sao Paulo"));
    }

    @Test
    void devePersistirUsuarioEOrganizacao() {
        User owner = user("doador@exemplo.test", UserRole.DONOR);
        Organization donor = organization(owner, "Padaria Exemplo", OrganizationType.DONOR);

        assertNotNull(owner.getId());
        assertTrue(users.existsByEmailIgnoreCase("DOADOR@EXEMPLO.TEST"));
        assertEquals(donor.getId(), organizations.findByOwnerId(owner.getId()).getFirst().getId());
    }

    @Test
    void devePersistirDoacaoEFiltrarDisponiveis() {
        Organization donor = organization(user("padaria@exemplo.test", UserRole.DONOR),
                "Padaria Exemplo", OrganizationType.DONOR);
        Donation donation = donations.saveAndFlush(new Donation(donor, "Pães do dia",
                FoodCategory.BAKERY, new BigDecimal("12.50"),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "Ambiente seco", true));

        assertNotNull(donation.getId());
        assertEquals(0, new BigDecimal("12.50").compareTo(donation.getQuantityKg()));
        assertTrue(donations.findByStatusOrderByExpiresAtAsc(DonationStatus.AVAILABLE)
                .stream().anyMatch(d -> d.getId().equals(donation.getId())));
    }

    @Test
    void devePersistirReservaESeusRelacionamentos() {
        Organization donor = organization(user("origem@exemplo.test", UserRole.DONOR),
                "Hortifruti Exemplo", OrganizationType.DONOR);
        Organization receiver = organization(user("destino@exemplo.test", UserRole.RECEIVER),
                "Cozinha Solidaria", OrganizationType.RECEIVER);
        Donation donation = donations.saveAndFlush(new Donation(donor, "Bananas",
                FoodCategory.FRUITS, new BigDecimal("8.00"),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(2), "Local fresco", true));
        Reservation reservation = reservations.saveAndFlush(new Reservation(donation, receiver));

        assertNotNull(reservation.getId());
        assertEquals(ReservationStatus.REQUESTED, reservation.getStatus());
        assertEquals(donation.getId(), reservations.findByDonationId(donation.getId())
                .getFirst().getDonation().getId());
        assertEquals(1, reservations.findByReceiverOrganizationId(receiver.getId()).size());
    }
}