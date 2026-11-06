package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendationApiIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DonationRepository donations;
    @Autowired ReceiverProfileRepository profiles;
    @Autowired PasswordEncoder encoder;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String PASS = "Teste123!Seguro";
    private final List<User> created = new ArrayList<>();

    private User user(UserRole role) {
        User u = users.saveAndFlush(new User("Teste", "rec-" + UUID.randomUUID() + "@example.org",
            encoder.encode(PASS), role));
        created.add(u);
        return u;
    }
    private Organization org(User owner, OrganizationType type, double latitude, double longitude) {
        Organization o = new Organization(owner, "Organizacao " + UUID.randomUUID(), type, "Sao Paulo");
        o.setCoordinates(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
        return organizations.saveAndFlush(o);
    }
    private Donation donation(Organization donor, FoodCategory category, BigDecimal kg, int hours) {
        return donations.saveAndFlush(new Donation(donor, "Alimentos teste", category, kg,
            OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours), "Armazenamento adequado", true));
    }
    private String profileJson(String categories, String cap, boolean available) {
        return "{\"capacityKg\":" + cap + ",\"availableForPickup\":" + available
            + ",\"acceptedCategories\":" + categories + "}";
    }
    private HttpResponse<String> call(String method, String path, String body, User user) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (user != null) request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
            (user.getEmail() + ":" + PASS).getBytes(StandardCharsets.UTF_8)));
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private int resultCount(HttpResponse<String> response) {
        String body = response.body().trim();
        if (body.equals("[]")) return 0;
        return body.split("\\\"donationId\\\"\\s*:").length - 1;
    }
    private long firstReceiverId(HttpResponse<String> response) {
        var matcher = java.util.regex.Pattern.compile("\\\"receiverOrganizationId\\\"\\s*:\\s*(\\d+)")
            .matcher(response.body());
        if (!matcher.find()) throw new AssertionError("Sem recebedor: " + response.body());
        return Long.parseLong(matcher.group(1));
    }
    private int[] scores(HttpResponse<String> response) {
        var matcher = java.util.regex.Pattern.compile("\\\"score\\\"\\s*:\\s*(\\d+)")
            .matcher(response.body());
        List<Integer> values = new ArrayList<>();
        while (matcher.find()) values.add(Integer.parseInt(matcher.group(1)));
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
    // Os testes usam um PostgreSQL persistente: outros receptores podem existir.
    // Conferimos apenas os receptores criados neste teste, sem exigir tamanho total da lista.
    private boolean containsReceiver(HttpResponse<String> response, Long organizationId) {
        return response.body().contains("\"receiverOrganizationId\":" + organizationId);
    }
    private int scoreForReceiver(HttpResponse<String> response, Long organizationId) {
        var objects = java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(response.body());
        while (objects.find()) {
            String item = objects.group();
            if (item.contains("\"receiverOrganizationId\":" + organizationId + ",")) {
                var matcher = java.util.regex.Pattern.compile("\"score\"\\s*:\\s*(\\d+)").matcher(item);
                if (matcher.find()) return Integer.parseInt(matcher.group(1));
            }
        }
        throw new AssertionError("Receptor " + organizationId + " nao encontrado: " + response.body());
    }
    private void clean() {
        // Apaga somente os dados criados por esta classe de teste.
        for (User u : created) {
            for (Organization o : organizations.findByOwnerId(u.getId())) {
                profiles.findById(o.getId()).ifPresent(p -> { profiles.delete(p); profiles.flush(); });
                for (Donation d : donations.findAll()) {
                    if (d.getOrganization().getId().equals(o.getId())) donations.delete(d);
                }
                donations.flush();
                organizations.delete(o);
            }
            organizations.flush();
            users.delete(u);
        }
        users.flush();
        created.clear();
    }

    @Test void rankingsPreferCloserEligibleReceiver() throws Exception {
        try {
            User donor = user(UserRole.DONOR), near = user(UserRole.RECEIVER), far = user(UserRole.RECEIVER);
            Donation d = donation(org(donor, OrganizationType.DONOR, -23.5505, -46.6333),
                FoodCategory.BAKERY, new BigDecimal("5.00"), 30);
            Organization close = org(near, OrganizationType.RECEIVER, -23.5506, -46.6334);
            Organization distant = org(far, OrganizationType.RECEIVER, -23.7000, -46.7000);
            for (var e : List.of(Map.entry(close, near), Map.entry(distant, far))) {
                var response = call("PUT", "/api/v1/receiver-profiles/" + e.getKey().getId(),
                    profileJson("[\"BAKERY\"]", "15.00", true), e.getValue());
                assertEquals(200, response.statusCode(), response.body());
            }
            var response = call("GET", "/api/v1/donations/" + d.getId() + "/recommendations", null, donor);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(containsReceiver(response, close.getId()), response.body());
            assertTrue(containsReceiver(response, distant.getId()), response.body());
            assertTrue(scoreForReceiver(response, close.getId()) >
                scoreForReceiver(response, distant.getId()), response.body());
            assertTrue(response.body().contains("linha reta"));
        } finally { clean(); }
    }

    @Test void incompatibleUnavailableAndInsufficientReceiversAreExcluded() throws Exception {
        try {
            User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER);
            Donation d = donation(org(donor, OrganizationType.DONOR, -23.5505, -46.6333),
                FoodCategory.FRUITS, new BigDecimal("8.00"), 30);
            Organization org = org(receiver, OrganizationType.RECEIVER, -23.5506, -46.6334);
            String endpoint = "/api/v1/receiver-profiles/" + org.getId();
            assertEquals(200, call("PUT", endpoint, profileJson("[\"BAKERY\"]", "15", true), receiver).statusCode());
            String rank = "/api/v1/donations/" + d.getId() + "/recommendations";
            assertFalse(containsReceiver(call("GET", rank, null, donor), org.getId()));
            assertEquals(200, call("PUT", endpoint, profileJson("[\"FRUITS\"]", "2", true), receiver).statusCode());
            assertFalse(containsReceiver(call("GET", rank, null, donor), org.getId()));
            assertEquals(200, call("PUT", endpoint, profileJson("[\"FRUITS\"]", "20", false), receiver).statusCode());
            assertFalse(containsReceiver(call("GET", rank, null, donor), org.getId()));
            assertEquals(200, call("PUT", endpoint, profileJson("[\"FRUITS\"]", "20", true), receiver).statusCode());
            assertTrue(containsReceiver(call("GET", rank, null, donor), org.getId()));
        } finally { clean(); }
    }

    @Test void expiredOrReservedDonationsAreNotRecommended() throws Exception {
        try {
            User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER);
            Donation d = donation(org(donor, OrganizationType.DONOR, -23.5505, -46.6333),
                FoodCategory.FRUITS, new BigDecimal("5.00"), 24);
            Organization org = org(receiver, OrganizationType.RECEIVER, -23.5506, -46.6334);
            assertEquals(200, call("PUT", "/api/v1/receiver-profiles/" + org.getId(),
                profileJson("[\"FRUITS\"]", "10", true), receiver).statusCode());
            String rank = "/api/v1/donations/" + d.getId() + "/recommendations";
            assertTrue(containsReceiver(call("GET", rank, null, donor), org.getId()));
            d.reserve(); donations.saveAndFlush(d);
            assertEquals(0, resultCount(call("GET", rank, null, donor)));
        } finally { clean(); }
    }

    @Test void receiverGetsOwnRecommendationsButNotAnotherReceivers() throws Exception {
        try {
            User donor = user(UserRole.DONOR), receiver = user(UserRole.RECEIVER), other = user(UserRole.RECEIVER);
            Donation d = donation(org(donor, OrganizationType.DONOR, -23.5505, -46.6333),
                FoodCategory.VEGETABLES, new BigDecimal("5.00"), 25);
            Organization own = org(receiver, OrganizationType.RECEIVER, -23.5506, -46.6334);
            Organization foreign = org(other, OrganizationType.RECEIVER, -23.5510, -46.6339);
            assertEquals(200, call("PUT", "/api/v1/receiver-profiles/" + own.getId(),
                profileJson("[\"VEGETABLES\"]", "10", true), receiver).statusCode());
            String path = "/api/v1/recommendations?receiverOrganizationId=" + own.getId();
            var response = call("GET", path, null, receiver);
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"donationId\":" + d.getId()));
            assertEquals(404, call("GET", "/api/v1/recommendations?receiverOrganizationId="
                + foreign.getId(), null, receiver).statusCode());
            assertEquals(404, call("GET", "/api/v1/donations/" + d.getId()
                + "/recommendations", null, receiver).statusCode());
        } finally { clean(); }
    }

    @Test void profileValidationAndUnauthorizedAccessAreBlocked() throws Exception {
        try {
            User receiver = user(UserRole.RECEIVER), stranger = user(UserRole.RECEIVER);
            Organization own = org(receiver, OrganizationType.RECEIVER, -23.5506, -46.6334);
            String path = "/api/v1/receiver-profiles/" + own.getId();
            assertEquals(401, call("GET", path, null, null).statusCode());
            assertEquals(404, call("PUT", path,
                profileJson("[\"BAKERY\"]", "10", true), stranger).statusCode());
            assertEquals(400, call("PUT", path,
                profileJson("[]", "0", true), receiver).statusCode());
            assertEquals(200, call("PUT", path,
                profileJson("[\"BAKERY\"]", "10", true), receiver).statusCode());
            assertEquals(200, call("GET", path, null, receiver).statusCode());
        } finally { clean(); }
    }
}
