package br.com.reaproveita;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;
import br.com.reaproveita.repository.UserRepository;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebUiIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void loginIsPublicAndContainsCsrf() throws Exception {
        var r = get("/login");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("name=\"_csrf\""));
        assertTrue(r.body().contains("Reaproveita"));
    }

    @Test void registerIsPublicAndDoesNotOfferAdmin() throws Exception {
        var r = get("/register");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("RECEIVER"));
        assertFalse(r.body().contains("value=\"ADMIN\""));
    }

    @Test void anonymousDashboardRedirectsToLogin() throws Exception {
        var r = get("/dashboard");
        assertEquals(302, r.statusCode());
        assertTrue(r.headers().firstValue("location").orElse("").contains("login"));
    }

    @Test void existingApiStillRequiresBasicAuthentication() throws Exception {
        var r = get("/api/v1/auth/me");
        assertEquals(401, r.statusCode());
    }

    @Test void webPostRequiresCsrfToken() throws Exception {
        var r = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/register"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("name=Teste&email=teste%40example.com&password=Senha1234&role=DONOR"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, r.statusCode());
    }

    @Test void authenticatedUserCanRenderAllProtectedScreens() throws Exception {
        String email = "ui-" + UUID.randomUUID() + "@example.org";
        User created = users.saveAndFlush(new User("Teste Interface", email, encoder.encode("Senha12345"), UserRole.DONOR));
        try {
            HttpClient session = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var login = session.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode());
            var matcher = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(matcher.find(), "Token CSRF esperado");
            String token = matcher.group(1);
            String body = "username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=Senha12345&_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            var signedIn = session.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, signedIn.statusCode());
            for (String path : new String[]{"/dashboard", "/organizations", "/donations", "/reservations", "/recommendations"}) {
                var page = session.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, page.statusCode(), "Tela: " + path);
                assertTrue(page.body().contains("Reaproveita"), "Layout: " + path);
            }
        } finally {
            users.deleteById(created.getId());
        }
    }

    @Test void healthAndStylesheetArePublic() throws Exception {
        assertEquals(200, get("/api/v1/health").statusCode());
        var css = get("/css/app.css");
        assertEquals(200, css.statusCode());
        assertTrue(css.body().contains("--green"));
    }
}
