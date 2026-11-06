package br.com.reaproveita;

import br.com.reaproveita.model.*;
import br.com.reaproveita.repository.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationApiIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired PasswordEncoder encoder;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String PASS = "TesteSeguro123!";

    private String email() { return "org-" + UUID.randomUUID() + "@example.org"; }
    private String url(String p) { return "http://localhost:" + port + p; }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("Usuario teste", email(), encoder.encode(PASS), role));
    }
    private HttpResponse<String> call(String method, String path, String body, User user) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url(path)));
        if (user != null) {
            var encoded = Base64.getEncoder().encodeToString((user.getEmail()+":"+PASS).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String createJson(String type) {
        return "{\"name\":\"Padaria Central\",\"type\":\""+type+"\",\"city\":\"Sao Paulo\",\"latitude\":-23.55052,\"longitude\":-46.63331}";
    }
    private String updateJson() {
        return "{\"name\":\"Padaria Renovada\",\"city\":\"Campinas\",\"latitude\":null,\"longitude\":null}";
    }
    private long id(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location").orElseThrow();
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
    private void cleanup(User... registered) {
        for (User u : registered) {
            if (u != null) {
                organizations.findByOwnerId(u.getId()).forEach(organizations::delete);
                organizations.flush();
                users.deleteById(u.getId());
            }
        }
    }

    @Test void donorCanCreateReadUpdateAndDeleteOwnOrganization() throws Exception {
        User donor = user(UserRole.DONOR);
        try {
            var created=call("POST","/api/v1/organizations",createJson("DONOR"),donor);
            assertEquals(201,created.statusCode(),created.body());
            long id=id(created);
            var fetched=call("GET","/api/v1/organizations/"+id,null,donor);
            assertEquals(200,fetched.statusCode(),fetched.body());
            assertTrue(fetched.body().contains("Padaria Central"));
            var updated=call("PUT","/api/v1/organizations/"+id,updateJson(),donor);
            assertEquals(200,updated.statusCode(),updated.body());
            assertTrue(updated.body().contains("Campinas"));
            assertTrue(updated.body().contains("DONOR"));
            assertEquals(204,call("DELETE","/api/v1/organizations/"+id,null,donor).statusCode());
            assertEquals(404,call("GET","/api/v1/organizations/"+id,null,donor).statusCode());
        } finally { cleanup(donor); }
    }

    @Test void receiverCanCreateAndListOwnButNotOtherOrganizations() throws Exception {
        User donor=user(UserRole.DONOR), receiver=user(UserRole.RECEIVER);
        try {
            long donorId=id(call("POST","/api/v1/organizations",createJson("DONOR"),donor));
            long receiverId=id(call("POST","/api/v1/organizations",createJson("RECEIVER"),receiver));
            var list=call("GET","/api/v1/organizations",null,receiver);
            assertEquals(200,list.statusCode(),list.body());
            String idKey = Character.toString((char)34) + "id" + (char)34 + ":";
            assertTrue(list.body().contains(idKey + receiverId), list.body());
            assertFalse(list.body().contains(idKey + donorId), list.body());
            assertEquals(404,call("GET","/api/v1/organizations/"+donorId,null,receiver).statusCode());
            assertEquals(404,call("PUT","/api/v1/organizations/"+donorId,updateJson(),receiver).statusCode());
            assertEquals(404,call("DELETE","/api/v1/organizations/"+donorId,null,receiver).statusCode());
        } finally { cleanup(receiver,donor); }
    }

    @Test void rejectsWrongOrganizationTypeAndInvalidData() throws Exception {
        User donor=user(UserRole.DONOR);
        try {
            assertEquals(403,call("POST","/api/v1/organizations",createJson("RECEIVER"),donor).statusCode());
            assertEquals(400,call("POST","/api/v1/organizations","{\"name\":\"\",\"type\":\"DONOR\",\"city\":\"\"}",donor).statusCode());
            assertEquals(400,call("POST","/api/v1/organizations","{\"name\":\"Teste\",\"type\":\"DONOR\",\"city\":\"Sao Paulo\",\"latitude\":95,\"longitude\":0}",donor).statusCode());
            assertEquals(400,call("POST","/api/v1/organizations","{\"name\":\"Teste\",\"type\":\"DONOR\",\"city\":\"Sao Paulo\",\"latitude\":-20}",donor).statusCode());
            assertTrue(organizations.findByOwnerId(donor.getId()).isEmpty());
        } finally { cleanup(donor); }
    }

    @Test void unauthenticatedCannotAccessOrganizations() throws Exception {
        assertEquals(401,call("GET","/api/v1/organizations",null,null).statusCode());
        assertEquals(401,call("POST","/api/v1/organizations",createJson("DONOR"),null).statusCode());
    }

    @Test void adminCanListAndManageOtherUsersOrganizations() throws Exception {
        User donor=user(UserRole.DONOR), admin=user(UserRole.ADMIN);
        try {
            long id=id(call("POST","/api/v1/organizations",createJson("DONOR"),donor));
            var list=call("GET","/api/v1/organizations",null,admin);
            assertEquals(200,list.statusCode(),list.body());
            assertTrue(list.body().contains("Padaria Central"));
            assertEquals(200,call("PUT","/api/v1/organizations/"+id,updateJson(),admin).statusCode());
            assertEquals(204,call("DELETE","/api/v1/organizations/"+id,null,admin).statusCode());
        } finally { cleanup(donor,admin); }
    }
}
