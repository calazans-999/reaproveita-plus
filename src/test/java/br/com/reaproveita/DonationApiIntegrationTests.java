package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.net.URI;
import java.net.http.*;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DonationApiIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DonationRepository donations;
    @Autowired PasswordEncoder encoder;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String PASS = "TesteSeguro123!";

    private String url(String path) { return "http://localhost:" + port + path; }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("Teste", "doac-" + UUID.randomUUID() + "@example.org",
            encoder.encode(PASS), role));
    }
    private Organization org(User owner, OrganizationType type) {
        return organizations.saveAndFlush(new Organization(owner, "Organizacao teste", type, "Sao Paulo"));
    }
    private String payload(Long orgId, String title, String quantity, String expiry, boolean sanitary) {
        return """
            {"organizationId":%d,"title":"%s","category":"BAKERY",
             "quantityKg":%s,"expiresAt":"%s",
             "storageCondition":"Ambiente seco","sanitaryDeclaration":%s}
            """.formatted(orgId, title, quantity, expiry, sanitary);
    }
    private String future() { return OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).toString(); }
    private String past() { return OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).toString(); }
    private HttpResponse<String> call(String method, String path, String json, User who) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url(path)));
        if (who != null) {
            var auth = Base64.getEncoder().encodeToString((who.getEmail()+":"+PASS).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + auth);
        }
        if (json != null) builder.header("Content-Type", "application/json");
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private long id(HttpResponse<String> created) {
        String location = created.headers().firstValue("Location").orElseThrow();
        return Long.parseLong(location.substring(location.lastIndexOf('/')+1));
    }
    private void cleanup(User... identities) {
        for (User u: identities) {
            if (u == null) continue;
            for (Organization o: organizations.findByOwnerId(u.getId())) {
                donations.findAll().stream()
                    .filter(d -> d.getOrganization().getId().equals(o.getId()))
                    .forEach(donations::delete);
                donations.flush();
                organizations.delete(o);
            }
            organizations.flush();
            users.deleteById(u.getId());
        }
    }

    @Test void donorCanCreateReadUpdateAndCancelDonation() throws Exception {
        User donor = user(UserRole.DONOR);
        try {
            Organization o = org(donor, OrganizationType.DONOR);
            var created = call("POST", "/api/v1/donations", payload(o.getId(),"Paes", "12.50", future(),true), donor);
            assertEquals(201, created.statusCode(), created.body());
            long donationId = id(created);
            assertEquals(200, call("GET", "/api/v1/donations/"+donationId,null,donor).statusCode());
            String update = """
                {"title":"Paes integrais","category":"BAKERY","quantityKg":8.5,
                 "expiresAt":"%s","storageCondition":"Seco","sanitaryDeclaration":true}
                """.formatted(future());
            var updated = call("PUT","/api/v1/donations/"+donationId,update,donor);
            assertEquals(200,updated.statusCode(),updated.body());
            assertTrue(updated.body().contains("Paes integrais"));
            var cancel = call("PATCH","/api/v1/donations/"+donationId+"/cancel",null,donor);
            assertEquals(200,cancel.statusCode(),cancel.body());
            assertTrue(cancel.body().contains("CANCELLED"));
            assertEquals(409,call("PATCH","/api/v1/donations/"+donationId+"/cancel",null,donor).statusCode());
        } finally { cleanup(donor); }
    }

    @Test void rejectsInvalidQuantityExpiryAndDeclaration() throws Exception {
        User donor = user(UserRole.DONOR);
        try {
            Organization o = org(donor,OrganizationType.DONOR);
            assertEquals(400,call("POST","/api/v1/donations",payload(o.getId(),"Paes","-5",future(),true),donor).statusCode());
            assertEquals(400,call("POST","/api/v1/donations",payload(o.getId(),"Paes","5",past(),true),donor).statusCode());
            assertEquals(400,call("POST","/api/v1/donations",payload(o.getId(),"Paes","5",future(),false),donor).statusCode());
        } finally { cleanup(donor); }
    }

    @Test void receiverCannotCreateAndDonorCannotUseSomeoneElsesOrg() throws Exception {
        User receiver=user(UserRole.RECEIVER), donor=user(UserRole.DONOR), other=user(UserRole.DONOR);
        try {
            Organization recvOrg=org(receiver, OrganizationType.RECEIVER);
            Organization donorOrg=org(donor, OrganizationType.DONOR);
            org(other, OrganizationType.DONOR);
            assertEquals(400,call("POST","/api/v1/donations",payload(recvOrg.getId(),"Paes","5",future(),true),receiver).statusCode());
            assertEquals(404,call("POST","/api/v1/donations",payload(donorOrg.getId(),"Paes","5",future(),true),other).statusCode());
            var created=call("POST","/api/v1/donations",payload(donorOrg.getId(),"Paes","5",future(),true),donor);
            assertEquals(201,created.statusCode(),created.body());
            long donationId=id(created);
            String update = """
                {"title":"Ataque","category":"BAKERY","quantityKg":5,
                 "expiresAt":"%s","storageCondition":"Seco","sanitaryDeclaration":true}
                """.formatted(future());
            assertEquals(404,call("PUT","/api/v1/donations/"+donationId,update,other).statusCode());
            assertEquals(404,call("PATCH","/api/v1/donations/"+donationId+"/cancel",null,receiver).statusCode());
        } finally { cleanup(other,receiver,donor); }
    }

    @Test void listsOnlyValidAvailableItemsAndFiltersCategory() throws Exception {
        User donor=user(UserRole.DONOR);
        try {
            Organization o=org(donor, OrganizationType.DONOR);
            var first=call("POST","/api/v1/donations",payload(o.getId(),"Teste Visivel", "5",future(),true),donor);
            assertEquals(201,first.statusCode(),first.body());
            var second=call("POST","/api/v1/donations",payload(o.getId(),"Teste Cancelado", "4",future(),true),donor);
            assertEquals(201,second.statusCode(),second.body());
            assertEquals(200,call("PATCH","/api/v1/donations/"+id(second)+"/cancel",null,donor).statusCode());
            var list=call("GET","/api/v1/donations?category=BAKERY",null,donor);
            assertEquals(200,list.statusCode(),list.body());
            assertTrue(list.body().contains("Teste Visivel"));
            assertFalse(list.body().contains("Teste Cancelado"));
        } finally { cleanup(donor); }
    }

    @Test void requiresAuthentication() throws Exception {
        assertEquals(401,call("GET","/api/v1/donations",null,null).statusCode());
        assertEquals(401,call("POST","/api/v1/donations","{}",null).statusCode());
    }
}
