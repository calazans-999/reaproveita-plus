
package br.com.reaproveita;

import br.com.reaproveita.dto.AiFoodAnalysisResponse;
import br.com.reaproveita.model.FoodCategory;
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

import java.math.BigDecimal;
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
@Import(AiFoodAnalysisSuccessIntegrationTests.FakeAiConfig.class)
class AiFoodAnalysisSuccessIntegrationTests {

    @TestConfiguration
    static class FakeAiConfig {

        @Bean
        AiModelClient fakeAiModelClient() {
            return description -> new AiFoodAnalysisResponse(
                FoodCategory.BAKERY,
                new BigDecimal("12.5"),
                null,
                "A descricao menciona paes.",
                false
            );
        }

        @Bean
        AiFoodAnalysisService fakeAiFoodAnalysisService(
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

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(
            String credentials,
            String description) throws Exception {

        String token = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8)
        );

        String json = """
            {"description":"%s"}
            """.formatted(description);

        HttpRequest request = HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port
                    + "/api/v1/ai/analyze-donation"
                )
            )
            .header("Authorization", "Basic " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        return http.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void donorReceivesValidAnalysis() throws Exception {

        String email = "ai-success-" + UUID.randomUUID()
            + "@example.org";

        User donor = users.saveAndFlush(
            new User(
                "Teste IA",
                email,
                encoder.encode("SenhaSegura123"),
                UserRole.DONOR
            )
        );

        try {
            var response = post(
                email + ":SenhaSegura123",
                "Tenho 12,5 kg de paes frescos"
            );

            assertEquals(
                200,
                response.statusCode(),
                response.body()
            );

            assertTrue(
                response.body().contains("\"category\":\"BAKERY\"")
            );

            assertTrue(
                response.body().contains("\"requiresReview\":true")
            );

            assertTrue(
                response.body().contains("\"quantityKg\":12.5")
            );

        } finally {
            users.deleteById(donor.getId());
        }
    }

    @Test
    void invalidDescriptionStillReceives400() throws Exception {

        String email = "ai-invalid-" + UUID.randomUUID()
            + "@example.org";

        User donor = users.saveAndFlush(
            new User(
                "Teste IA",
                email,
                encoder.encode("SenhaSegura123"),
                UserRole.DONOR
            )
        );

        try {
            var response = post(
                email + ":SenhaSegura123",
                "abc"
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
