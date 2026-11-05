package br.com.reaproveita;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;
import br.com.reaproveita.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSecurityIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    private final HttpClient http = HttpClient.newHttpClient();

    private String uniqueEmail() { return "test-" + UUID.randomUUID() + "@example.org"; }
    private String url(String path) { return "http://localhost:" + port + path; }
    private String payload(String email, String role, String password) {
        return "{\"name\":\"Usuario de Teste\",\"email\":\""+email+"\",\"password\":\""+password+"\",\"role\":\""+role+"\"}";
    }
    private HttpResponse<String> send(String method, String path, String body, String basic) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url(path)));
        if (basic != null) {
            String token = Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private void cleanup(String email) { users.findByEmailIgnoreCase(email).ifPresent(users::delete); }

    @Test void registrationHashesPasswordAndAuthenticates() throws Exception {
        String email = uniqueEmail();
        try {
            var created = send("POST", "/api/v1/auth/register", payload(email,"DONOR","SenhaSegura123"), null);
            assertEquals(201, created.statusCode(), created.body());
            assertFalse(created.body().contains("SenhaSegura123"));
            assertFalse(created.body().contains("passwordHash"));
            var saved = users.findByEmailIgnoreCase(email).orElseThrow();
            assertTrue(saved.getPasswordHash().startsWith("$2"));
            assertTrue(passwordEncoder.matches("SenhaSegura123", saved.getPasswordHash()));
            var me = send("GET", "/api/v1/auth/me", null, email + ":SenhaSegura123");
            assertEquals(200, me.statusCode(), me.body());
            assertTrue(me.body().contains(email));
        } finally { cleanup(email); }
    }

    @Test void duplicateEmailReturnsConflict() throws Exception {
        String email = uniqueEmail();
        try {
            assertEquals(201,send("POST","/api/v1/auth/register",payload(email,"DONOR","SenhaSegura123"),null).statusCode());
            assertEquals(409,send("POST","/api/v1/auth/register",payload(email.toUpperCase(),"DONOR","SenhaSegura123"),null).statusCode());
        } finally { cleanup(email); }
    }

    @Test void invalidPasswordReturnsBadRequest() throws Exception {
        String email = uniqueEmail();
        try {
            var result = send("POST","/api/v1/auth/register",payload(email,"DONOR","123"),null);
            assertEquals(400,result.statusCode(),result.body());
            assertFalse(users.existsByEmailIgnoreCase(email));
        } finally { cleanup(email); }
    }

    @Test void publicCannotRegisterAdmin() throws Exception {
        String email = uniqueEmail();
        try {
            var result = send("POST","/api/v1/auth/register",payload(email,"ADMIN","SenhaSegura123"),null);
            assertEquals(400,result.statusCode(),result.body());
            assertFalse(users.existsByEmailIgnoreCase(email));
        } finally { cleanup(email); }
    }

    @Test void rolesAndUnauthenticatedRequestsAreRestricted() throws Exception {
        String donor = uniqueEmail();
        String receiver = uniqueEmail();
        String admin = uniqueEmail();
        try {
            assertEquals(401,send("GET","/api/v1/auth/me",null,null).statusCode());
            assertEquals(201,send("POST","/api/v1/auth/register",payload(donor,"DONOR","SenhaSegura123"),null).statusCode());
            assertEquals(201,send("POST","/api/v1/auth/register",payload(receiver,"RECEIVER","SenhaSegura123"),null).statusCode());
            users.saveAndFlush(new User("Admin de teste",admin,passwordEncoder.encode("SenhaSegura123"),UserRole.ADMIN));
            assertEquals(200,send("GET","/api/v1/donor/ping",null,donor+":SenhaSegura123").statusCode());
            assertEquals(403,send("GET","/api/v1/receiver/ping",null,donor+":SenhaSegura123").statusCode());
            assertEquals(200,send("GET","/api/v1/receiver/ping",null,receiver+":SenhaSegura123").statusCode());
            assertEquals(403,send("GET","/api/v1/admin/ping",null,receiver+":SenhaSegura123").statusCode());
            assertEquals(200,send("GET","/api/v1/admin/ping",null,admin+":SenhaSegura123").statusCode());
            assertEquals(401,send("GET","/api/v1/auth/me",null,donor+":senhaErrada").statusCode());
        } finally { cleanup(donor); cleanup(receiver); cleanup(admin); }
    }
}
