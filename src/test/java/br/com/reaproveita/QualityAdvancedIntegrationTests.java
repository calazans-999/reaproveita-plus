package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Etapa 9: casos de regressao e fluxos negativos. Os testes criam seus proprios
 * dados e removem apenas os dados associados aos usuarios gerados por eles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QualityAdvancedIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DonationRepository donations;
    @Autowired ReservationRepository reservations;
    @Autowired PasswordEncoder encoder;

    private static final String PASSWORD = "SenhaDeTeste123!";
    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    private User user(UserRole role) {
        return users.saveAndFlush(new User("Qualidade Etapa 9",
            "qa9-" + UUID.randomUUID() + "@example.org", encoder.encode(PASSWORD), role));
    }

    private Organization organization(User owner, OrganizationType type) {
        return organizations.saveAndFlush(new Organization(owner,
            "Organizacao QA " + UUID.randomUUID(), type, "Sao Paulo"));
    }

    private Donation donation(Organization donor) {
        return donations.saveAndFlush(new Donation(donor, "Paes teste qualidade",
            FoodCategory.BAKERY, new BigDecimal("4.00"),
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(2), "Ambiente seco", true));
    }

    private HttpResponse<String> call(String method, String path, String json, User user)
            throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            String value = Base64.getEncoder().encodeToString(
                (user.getEmail() + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            req.header("Authorization", "Basic " + value);
        }
        if (json != null) req.header("Content-Type", "application/json");
        req.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json));
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String reservationJson(Organization receiver) {
        return "{\"receiverOrganizationId\":" + receiver.getId() + "}";
    }

    private long id(HttpResponse<String> created) {
        return Long.parseLong(created.headers().firstValue("Location").orElseThrow()
            .replaceAll(".*/", ""));
    }

    private void cleanup(User... generatedUsers) {
        for (User u : generatedUsers) {
            for (Organization org : organizations.findByOwnerId(u.getId())) {
                for (Reservation r : reservations.findByReceiverOrganizationId(org.getId())) {
                    reservations.deleteById(r.getId());
                }
                for (Donation d : donations.findAll()) {
                    if (d.getOrganization().getId().equals(org.getId())) {
                        for (Reservation r : reservations.findByDonationId(d.getId())) {
                            reservations.deleteById(r.getId());
                        }
                        reservations.flush();
                        donations.deleteById(d.getId());
                    }
                }
                donations.flush();
                organizations.deleteById(org.getId());
            }
            organizations.flush();
            users.deleteById(u.getId());
        }
        users.flush();
    }

    @Test
    void anonymousSensitiveEndpointsRequireAuthentication() throws Exception {
        assertEquals(401, call("GET", "/api/v1/auth/me", null, null).statusCode());
        assertEquals(401, call("GET", "/api/v1/donations", null, null).statusCode());
        assertEquals(401, call("GET", "/api/v1/reservations/999999999", null, null).statusCode());
        assertEquals(401, call("GET", "/api/v1/recommendations?receiverOrganizationId=1", null, null).statusCode());
    }

    @Test
    void donorCannotCallReceiverOrAdminOnlyRoutes() throws Exception {
        User donor = user(UserRole.DONOR);
        try {
            assertEquals(200, call("GET", "/api/v1/donor/ping", null, donor).statusCode());
            assertEquals(403, call("GET", "/api/v1/receiver/ping", null, donor).statusCode());
            assertEquals(403, call("GET", "/api/v1/admin/ping", null, donor).statusCode());
        } finally { cleanup(donor); }
    }

    @Test
    void invalidRegistrationCannotCreateAnAccount() throws Exception {
        String email = "qa9-invalid-" + UUID.randomUUID() + "@example.org";
        String json = "{\"name\":\"QA\",\"email\":\"" + email
            + "\",\"password\":\"123\",\"role\":\"ADMIN\"}";
        var response = call("POST", "/api/v1/auth/register", json, null);
        assertEquals(400, response.statusCode(), response.body());
        assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test
    void outsiderCannotReadOrModifyAnotherOrganizationsReservation() throws Exception {
        User donor = user(UserRole.DONOR);
        User receiver = user(UserRole.RECEIVER);
        User outsider = user(UserRole.RECEIVER);
        try {
            Donation d = donation(organization(donor, OrganizationType.DONOR));
            Organization r = organization(receiver, OrganizationType.RECEIVER);
            organization(outsider, OrganizationType.RECEIVER);
            var created = call("POST", "/api/v1/donations/" + d.getId() + "/reservations",
                reservationJson(r), receiver);
            assertEquals(201, created.statusCode(), created.body());
            String url = "/api/v1/reservations/" + id(created);
            assertEquals(404, call("GET", url, null, outsider).statusCode());
            assertEquals(404, call("PATCH", url + "/confirm", null, outsider).statusCode());
            assertEquals(404, call("PATCH", url + "/cancel", null, outsider).statusCode());
            assertEquals(ReservationStatus.REQUESTED,
                reservations.findById(id(created)).orElseThrow().getStatus());
        } finally { cleanup(outsider, receiver, donor); }
    }

    @Test
    void cancelledReservationCannotBeConfirmedAndDonationCanBeRebooked() throws Exception {
        User donor = user(UserRole.DONOR);
        User receiver = user(UserRole.RECEIVER);
        try {
            Donation d = donation(organization(donor, OrganizationType.DONOR));
            Organization r = organization(receiver, OrganizationType.RECEIVER);
            String url = "/api/v1/donations/" + d.getId() + "/reservations";
            var first = call("POST", url, reservationJson(r), receiver);
            assertEquals(201, first.statusCode(), first.body());
            String old = "/api/v1/reservations/" + id(first);
            assertEquals(200, call("PATCH", old + "/cancel", null, receiver).statusCode());
            assertEquals(409, call("PATCH", old + "/confirm", null, donor).statusCode());
            assertEquals(409, call("PATCH", old + "/collect", null, donor).statusCode());
            assertEquals(DonationStatus.AVAILABLE, donations.findById(d.getId()).orElseThrow().getStatus());
            var second = call("POST", url, reservationJson(r), receiver);
            assertEquals(201, second.statusCode(), second.body());
            assertNotEquals(id(first), id(second));
        } finally { cleanup(receiver, donor); }
    }

    @Test
    void collectedDonationCannotBeReservedAgainOrCancelled() throws Exception {
        User donor = user(UserRole.DONOR);
        User receiver = user(UserRole.RECEIVER);
        try {
            Donation d = donation(organization(donor, OrganizationType.DONOR));
            Organization r = organization(receiver, OrganizationType.RECEIVER);
            String donationUrl = "/api/v1/donations/" + d.getId() + "/reservations";
            var created = call("POST", donationUrl, reservationJson(r), receiver);
            assertEquals(201, created.statusCode(), created.body());
            String reservedUrl = "/api/v1/reservations/" + id(created);
            assertEquals(200, call("PATCH", reservedUrl + "/confirm", null, donor).statusCode());
            assertEquals(200, call("PATCH", reservedUrl + "/collect", null, donor).statusCode());
            assertEquals(409, call("PATCH", reservedUrl + "/cancel", null, receiver).statusCode());
            assertEquals(409, call("POST", donationUrl, reservationJson(r), receiver).statusCode());
            assertEquals(DonationStatus.COLLECTED, donations.findById(d.getId()).orElseThrow().getStatus());
        } finally { cleanup(receiver, donor); }
    }
}
