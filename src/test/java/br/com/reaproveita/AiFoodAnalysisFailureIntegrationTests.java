
package br.com.reaproveita;

import br.com.reaproveita.model.User;
import br.com.reaproveita.model.UserRole;
import br.com.reaproveita.repository.UserRepository;
import br.com.reaproveita.service.AiFoodAnalysisService;
import br.com.reaproveita.service.AiModelClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
@Import(AiFoodAnalysisFailureIntegrationTests.FakeFailureConfig.class)
class AiFoodAnalysisFailureIntegrationTests {

    @TestConfiguration
    static class FakeFailureConfig {

        @Bean
        AiModelClient failingAiModelClient() {
            return description -> {
                throw new IllegalStateException(
                    "Erro interno simulado: API_KEY_SECRETA_TESTE"
                );
            };
        }

        @Bean
        AiFoodAnalysisService failureAiFoodAnalysisService(
                AiModelClient client) {
            return new AiFoodAnalysisService(client);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void unavailableModelReturns503WithoutExposingSecrets()
            throws Exception {

        String email = "ai-failure-" + UUID.randomUUID()
            + "@example.org";

        User donor = users.saveAndFlush(
            new User(
                "Teste Falha IA",
                email,
                encoder.encode("SenhaSegura123"),
                UserRole.DONOR
            )
        );

        try {
            String credentials = email + ":SenhaSegura123";

            String token = Base64.getEncoder()
                .encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8)
                );

            String json = """
                {
                  "description": "Tenho 12 kg de paes para doar"
                }
                """;

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + port
                        + "/api/v1/ai/analyze-donation"
                    )
                )
                .header("Authorization", "Basic " + token)
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(json)
                )
                .build();

            HttpResponse<String> response =
                HttpClient.newHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

            assertEquals(
                503,
                response.statusCode(),
                response.body()
            );

            assertTrue(
                response.body().contains(
                    "AI_SERVICE_UNAVAILABLE"
                )
            );

            assertFalse(
                response.body().contains(
                    "API_KEY_SECRETA_TESTE"
                )
            );

            assertFalse(
                response.body().contains(
                    "Erro interno simulado"
                )
            );

        } finally {
            users.deleteById(donor.getId());
        }
    }
}
