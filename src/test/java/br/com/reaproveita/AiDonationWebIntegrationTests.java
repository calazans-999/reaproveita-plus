
package br.com.reaproveita;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;
import br.com.reaproveita.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "reaproveita.analysis.mode=off"
)
class AiDonationWebIntegrationTests {

    private static final String PASSWORD = "SenhaSegura123";

    private static final String ENDPOINT =
        "/donations/ai/analyze";

    private static final String JSON = """
        {
          "description": "Tenho 12 kg de paes para doar"
        }
        """;

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpClient newSession() {
        return HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    private HttpResponse<String> get(
            HttpClient client,
            String path) throws Exception {

        return client.send(
            HttpRequest.newBuilder(URI.create(url(path)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private String csrfToken(String html) {
        var matcher = Pattern.compile(
            "name=\"_csrf\"\\s+value=\"([^\"]+)\""
        ).matcher(html);

        assertTrue(
            matcher.find(),
            "Token CSRF nao encontrado na pagina"
        );

        return matcher.group(1);
    }

    private User createUser(UserRole role) {

        String email = "ai-web-" + UUID.randomUUID()
            + "@example.org";

        return users.saveAndFlush(
            new User(
                "Usuario Teste IA Web",
                email,
                encoder.encode(PASSWORD),
                role
            )
        );
    }

    private String login(HttpClient client, User user)
            throws Exception {

        var loginPage = get(client, "/login");

        assertEquals(200, loginPage.statusCode());

        String token = csrfToken(loginPage.body());

        String form = "username="
            + URLEncoder.encode(
                user.getEmail(),
                StandardCharsets.UTF_8
            )
            + "&password="
            + URLEncoder.encode(
                PASSWORD,
                StandardCharsets.UTF_8
            )
            + "&_csrf="
            + URLEncoder.encode(
                token,
                StandardCharsets.UTF_8
            );

        var response = client.send(
            HttpRequest.newBuilder(URI.create(url("/login")))
                .header(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                .POST(
                    HttpRequest.BodyPublishers.ofString(form)
                )
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(
            302,
            response.statusCode(),
            "Login deveria redirecionar"
        );

        var donationsPage = get(client, "/donations");

        assertEquals(
            200,
            donationsPage.statusCode(),
            donationsPage.body()
        );

        return csrfToken(donationsPage.body());
    }

    private HttpResponse<String> analyze(
            HttpClient client,
            String csrf) throws Exception {

        var builder = HttpRequest.newBuilder(
                URI.create(url(ENDPOINT))
            )
            .header("Content-Type", "application/json");

        if (csrf != null) {
            builder.header("X-CSRF-TOKEN", csrf);
        }

        return client.send(
            builder.POST(
                HttpRequest.BodyPublishers.ofString(JSON)
            ).build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void anonymousUserCannotAccessDonationsPage()
            throws Exception {

        var client = newSession();

        var response = get(client, "/donations");

        assertEquals(302, response.statusCode());

        assertTrue(
            response.headers()
                .firstValue("location")
                .orElse("")
                .contains("login")
        );
    }

    @Test
    void postWithoutCsrfIsForbidden()
            throws Exception {

        User donor = createUser(UserRole.DONOR);

        try {
            var client = newSession();

            login(client, donor);

            var response = analyze(client, null);

            assertEquals(
                403,
                response.statusCode(),
                response.body()
            );
        } finally {
            users.deleteById(donor.getId());
        }
    }

    @Test
    void receiverCannotUseAiEvenWithValidCsrf()
            throws Exception {

        User receiver = createUser(UserRole.RECEIVER);

        try {
            var client = newSession();

            String token = login(client, receiver);

            var response = analyze(client, token);

            assertEquals(
                403,
                response.statusCode(),
                response.body()
            );
        } finally {
            users.deleteById(receiver.getId());
        }
    }

    @Test
    void donorReceives503WhenAiDisabled()
            throws Exception {

        User donor = createUser(UserRole.DONOR);

        try {
            var client = newSession();

            String token = login(client, donor);

            var response = analyze(client, token);

            assertEquals(
                503,
                response.statusCode(),
                response.body()
            );
        } finally {
            users.deleteById(donor.getId());
        }
    }
}
