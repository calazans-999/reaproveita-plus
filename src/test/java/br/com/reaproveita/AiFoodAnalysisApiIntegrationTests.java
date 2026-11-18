
package br.com.reaproveita;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;
import br.com.reaproveita.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "reaproveita.analysis.mode=off"
)
class AiFoodAnalysisApiIntegrationTests {

    private static final String ENDPOINT =
        "/api/v1/ai/analyze-donation";

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newHttpClient();

    private String url() {
        return "http://localhost:" + port + ENDPOINT;
    }

    private HttpResponse<String> post(
            String credentials,
            String json) throws Exception {

        var builder = HttpRequest.newBuilder(URI.create(url()))
            .header("Content-Type", "application/json");

        if (credentials != null) {
            String token = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
            );
            builder.header("Authorization", "Basic " + token);
        }

        return http.send(
            builder.POST(
                HttpRequest.BodyPublishers.ofString(json)
            ).build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private User createUser(UserRole role) {
        String email = "ai-" + UUID.randomUUID() + "@example.org";

        return users.saveAndFlush(
            new User(
                "Usuario Teste IA",
                email,
                encoder.encode("SenhaSegura123"),
                role
            )
        );
    }

    private String credentials(User user) {
        return user.getEmail() + ":SenhaSegura123";
    }

    private String validJson() {
        return """
            {
              "description": "Tenho 12,5 kg de paes frescos para doar"
            }
            """;
    }

    @Test
    void unauthenticatedUserReceives401() throws Exception {
        var response = post(null, validJson());

        assertEquals(401, response.statusCode());
    }

    @Test
    void receiverReceives403() throws Exception {
        User receiver = createUser(UserRole.RECEIVER);

        try {
            var response = post(
                credentials(receiver),
                validJson()
            );

            assertEquals(403, response.statusCode());
        } finally {
            users.deleteById(receiver.getId());
        }
    }

    @Test
    void donorReceives503WhenAiDisabled() throws Exception {
        User donor = createUser(UserRole.DONOR);

        try {
            var response = post(
                credentials(donor),
                validJson()
            );

            assertEquals(503, response.statusCode());
        } finally {
            users.deleteById(donor.getId());
        }
    }

    @Test
    void adminReceives503WhenAiDisabled() throws Exception {
        User admin = createUser(UserRole.ADMIN);

        try {
            var response = post(
                credentials(admin),
                validJson()
            );

            assertEquals(503, response.statusCode());
        } finally {
            users.deleteById(admin.getId());
        }
    }

    @Test
    void invalidDescriptionReceives400() throws Exception {
        User donor = createUser(UserRole.DONOR);

        try {
            var response = post(
                credentials(donor),
                """
                {
                  "description": "abc"
                }
                """
            );

            assertEquals(
                400,
                response.statusCode(),
                response.body()
            );
        } finally {
            users.deleteById(donor.getId());
        }
    }
}
