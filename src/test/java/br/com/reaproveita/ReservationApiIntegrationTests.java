package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationApiIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DonationRepository donations;
    @Autowired ReservationRepository reservations;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String PASSWORD = "Teste123!Seguro";

    private User user(UserRole role) {
        return users.saveAndFlush(new User("Usuario Teste",
            "reserva-" + UUID.randomUUID() + "@example.org", encoder.encode(PASSWORD), role));
    }
    private Organization org(User owner, OrganizationType type) {
        return organizations.saveAndFlush(new Organization(owner, "Organizacao Teste", type, "Sao Paulo"));
    }
    private Donation donation(Organization donor) {
        return donations.saveAndFlush(new Donation(donor, "Paes", FoodCategory.BAKERY,
            new BigDecimal("5.00"), OffsetDateTime.now(ZoneOffset.UTC).plusDays(2), "Ambiente seco", true));
    }
    private HttpRequest request(String method, String path, String json, User user) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) {
            String basic = Base64.getEncoder().encodeToString(
                (user.getEmail() + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }
        if (json != null) builder.header("Content-Type", "application/json");
        return builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json)).build();
    }
    private HttpResponse<String> call(String method, String path, String json, User user) throws Exception {
        return http.send(request(method, path, json, user), HttpResponse.BodyHandlers.ofString());
    }
    private String createJson(Organization receiver) {
        return "{\"receiverOrganizationId\":" + receiver.getId() + "}";
    }
    private long createdId(HttpResponse<String> response) {
        return Long.parseLong(response.headers().firstValue("Location").orElseThrow()
            .replaceAll(".*/", ""));
    }
    private void cleanup(User... testUsers) {
        // Apaga exclusivamente as entidades dos usuarios criados por este teste.
        for (User user : testUsers) {
            for (Organization org : organizations.findByOwnerId(user.getId())) {
                for (Reservation r : reservations.findByReceiverOrganizationId(org.getId()))
                    reservations.deleteById(r.getId());
                for (Donation d : donations.findAll()) {
                    if (d.getOrganization().getId().equals(org.getId())) {
                        for (Reservation r : reservations.findByDonationId(d.getId()))
                            reservations.deleteById(r.getId());
                        reservations.flush();
                        donations.deleteById(d.getId());
                    }
                }
                donations.flush();
                organizations.deleteById(org.getId());
            }
            organizations.flush();
            users.deleteById(user.getId());
        }
    }

    @Test void fullLifecycleRequestConfirmCollect() throws Exception {
        User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER);
        try {
            Donation d = donation(org(donor, OrganizationType.DONOR));
            Organization recv = org(receiver, OrganizationType.RECEIVER);
            var created = call("POST", "/api/v1/donations/" + d.getId() + "/reservations", createJson(recv), receiver);
            assertEquals(201, created.statusCode(), created.body());
            long id = createdId(created);
            assertEquals(DonationStatus.RESERVED, donations.findById(d.getId()).orElseThrow().getStatus());
            assertEquals(200, call("GET", "/api/v1/reservations/" + id, null, receiver).statusCode());
            var confirmed = call("PATCH", "/api/v1/reservations/" + id + "/confirm", null, donor);
            assertEquals(200, confirmed.statusCode(), confirmed.body());
            assertTrue(confirmed.body().contains("CONFIRMED"));
            var collected = call("PATCH", "/api/v1/reservations/" + id + "/collect", null, donor);
            assertEquals(200, collected.statusCode(), collected.body());
            assertTrue(collected.body().contains("COLLECTED"));
            assertEquals(DonationStatus.COLLECTED, donations.findById(d.getId()).orElseThrow().getStatus());
            assertNotNull(reservations.findById(id).orElseThrow().getCollectedAt());
            assertEquals(409, call("PATCH", "/api/v1/reservations/" + id + "/cancel", null, receiver).statusCode());
        } finally { cleanup(receiver, donor); }
    }

    @Test void cancelReleasesDonationAndAllowsNewReservation() throws Exception {
        User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER);
        try {
            Donation d = donation(org(donor, OrganizationType.DONOR));
            Organization recv = org(receiver, OrganizationType.RECEIVER);
            var first = call("POST", "/api/v1/donations/" + d.getId() + "/reservations", createJson(recv), receiver);
            assertEquals(201, first.statusCode(), first.body());
            long id = createdId(first);
            var cancelled = call("PATCH", "/api/v1/reservations/" + id + "/cancel", null, receiver);
            assertEquals(200, cancelled.statusCode(), cancelled.body());
            assertTrue(cancelled.body().contains("CANCELLED"));
            assertEquals(DonationStatus.AVAILABLE, donations.findById(d.getId()).orElseThrow().getStatus());
            assertEquals(201, call("POST", "/api/v1/donations/" + d.getId() + "/reservations", createJson(recv), receiver).statusCode());
        } finally { cleanup(receiver, donor); }
    }

    @Test void profileOwnershipAndUnauthenticatedAreEnforced() throws Exception {
        User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER), outsider = user(UserRole.RECEIVER);
        try {
            Donation d = donation(org(donor, OrganizationType.DONOR));
            Organization recv = org(receiver, OrganizationType.RECEIVER);
            var other = org(outsider, OrganizationType.RECEIVER);
            String path = "/api/v1/donations/" + d.getId() + "/reservations";
            assertEquals(401, call("POST", path, createJson(recv), null).statusCode());
            assertEquals(403, call("POST", path, createJson(recv), donor).statusCode());
            assertEquals(404, call("POST", path, createJson(recv), outsider).statusCode());
            var created = call("POST", path, createJson(recv), receiver);
            assertEquals(201, created.statusCode(), created.body());
            String reservation = "/api/v1/reservations/" + createdId(created);
            assertEquals(404, call("GET", reservation, null, outsider).statusCode());
            assertEquals(404, call("PATCH", reservation + "/confirm", null, outsider).statusCode());
            assertEquals(404, call("PATCH", reservation + "/collect", null, outsider).statusCode());
            assertEquals(404, call("PATCH", reservation + "/cancel", null, outsider).statusCode());
        } finally { cleanup(outsider, receiver, donor); }
    }

    @Test void expiredOrInvalidReceiverAndInvalidTransitionsAreRejected() throws Exception {
        User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER);
        try {
            Organization donorOrg = org(donor, OrganizationType.DONOR);
            Organization recv = org(receiver, OrganizationType.RECEIVER);
            Donation d = donation(donorOrg);
            String path = "/api/v1/donations/" + d.getId() + "/reservations";
            Organization invalidReceiver = org(receiver, OrganizationType.DONOR);
            assertEquals(400, call("POST", path, createJson(invalidReceiver), receiver).statusCode());
            assertEquals(400, call("POST", path, "{}", receiver).statusCode());
            var created = call("POST", path, createJson(recv), receiver);
            assertEquals(201, created.statusCode(), created.body());
            long reservationId = createdId(created);
            assertEquals(409, call("PATCH", "/api/v1/reservations/" + reservationId + "/collect", null, donor).statusCode());
            assertEquals(200, call("PATCH", "/api/v1/reservations/" + reservationId + "/cancel", null, donor).statusCode());
            jdbc.update("UPDATE donations SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE id = ?", d.getId());
            assertEquals(409, call("POST", path, createJson(recv), receiver).statusCode());
        } finally { cleanup(receiver, donor); }
    }

    @Test void concurrentRequestsAllowExactlyOneActiveReservation() throws Exception {
        User donor = user(UserRole.DONOR), receiverA = user(UserRole.RECEIVER), receiverB = user(UserRole.RECEIVER);
        try {
            Donation d = donation(org(donor, OrganizationType.DONOR));
            Organization a = org(receiverA, OrganizationType.RECEIVER);
            Organization b = org(receiverB, OrganizationType.RECEIVER);
            String path = "/api/v1/donations/" + d.getId() + "/reservations";
            var fa = http.sendAsync(request("POST", path, createJson(a), receiverA), HttpResponse.BodyHandlers.ofString());
            var fb = http.sendAsync(request("POST", path, createJson(b), receiverB), HttpResponse.BodyHandlers.ofString());
            var ra = fa.join(); var rb = fb.join();
            assertTrue((ra.statusCode() == 201 && rb.statusCode() == 409)
                || (ra.statusCode() == 409 && rb.statusCode() == 201), ra.body() + " / " + rb.body());
            assertEquals(1, reservations.findByDonationId(d.getId()).stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED).count());
        } finally { cleanup(receiverA, receiverB, donor); }
    }
}