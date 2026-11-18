
package br.com.reaproveita;

import br.com.reaproveita.dto.AiFoodAnalysisRequest;
import br.com.reaproveita.dto.AiFoodAnalysisResponse;
import br.com.reaproveita.model.FoodCategory;
import br.com.reaproveita.service.AiFoodAnalysisService;
import br.com.reaproveita.service.AiModelClient;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiFoodAnalysisServiceTests {

    private AiFoodAnalysisResponse validResponse() {
        return new AiFoodAnalysisResponse(
            FoodCategory.BAKERY,
            new BigDecimal("12.5"),
            null,
            "A descricao menciona paes frescos.",
            false
        );
    }

    @Test
    void shouldAnalyzeValidFoodDescription() {
        AiModelClient client = description -> validResponse();

        var service = new AiFoodAnalysisService(client);

        var result = service.analyze(
            new AiFoodAnalysisRequest(
                "Tenho 12,5 kg de paes frescos para doar"
            )
        );

        assertEquals(FoodCategory.BAKERY, result.category());
        assertEquals(
            0,
            new BigDecimal("12.5").compareTo(result.quantityKg())
        );
        assertTrue(result.requiresReview());
    }

    @Test
    void shouldRejectNullRequest() {
        AiModelClient client = description -> validResponse();

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.analyze(null)
        );
    }

    @Test
    void shouldRejectShortDescription() {
        AiModelClient client = description -> validResponse();

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.analyze(
                new AiFoodAnalysisRequest("abc")
            )
        );
    }

    @Test
    void shouldRejectDescriptionAboveLimit() {
        AiModelClient client = description -> validResponse();

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.analyze(
                new AiFoodAnalysisRequest("a".repeat(1001))
            )
        );
    }

    @Test
    void shouldRejectMissingCategory() {
        AiModelClient client = description ->
            new AiFoodAnalysisResponse(
                null,
                null,
                null,
                "Categoria nao identificada",
                true
            );

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalStateException.class,
            () -> service.analyze(
                new AiFoodAnalysisRequest(
                    "Alimentos variados para doacao"
                )
            )
        );
    }

    @Test
    void shouldRejectNegativeQuantity() {
        AiModelClient client = description ->
            new AiFoodAnalysisResponse(
                FoodCategory.FRUITS,
                new BigDecimal("-5"),
                null,
                "Quantidade invalida",
                true
            );

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalStateException.class,
            () -> service.analyze(
                new AiFoodAnalysisRequest(
                    "Cinco quilos de frutas"
                )
            )
        );
    }

    @Test
    void shouldTrimDescriptionBeforeSendingToModel() {
        AtomicReference<String> received =
            new AtomicReference<>();

        AiModelClient client = description -> {
            received.set(description);
            return validResponse();
        };

        var service = new AiFoodAnalysisService(client);

        service.analyze(
            new AiFoodAnalysisRequest(
                "   Paes frescos para doacao   "
            )
        );

        assertEquals(
            "Paes frescos para doacao",
            received.get()
        );
    }

    @Test
    void shouldRequireHumanReview() {
        AiModelClient client = description -> validResponse();

        var service = new AiFoodAnalysisService(client);

        var result = service.analyze(
            new AiFoodAnalysisRequest(
                "Paes frescos disponiveis hoje"
            )
        );

        assertTrue(result.requiresReview());
    }

    @Test
    void shouldRejectNullModelResponse() {
        AiModelClient client = description -> null;

        var service = new AiFoodAnalysisService(client);

        assertThrows(
            IllegalStateException.class,
            () -> service.analyze(
                new AiFoodAnalysisRequest(
                    "Alimentos disponiveis para doacao"
                )
            )
        );
    }
}
