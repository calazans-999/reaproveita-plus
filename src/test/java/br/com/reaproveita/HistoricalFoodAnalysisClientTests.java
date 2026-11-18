
package br.com.reaproveita;

import br.com.reaproveita.model.Donation;
import br.com.reaproveita.model.DonationStatus;
import br.com.reaproveita.model.FoodCategory;
import br.com.reaproveita.repository.DonationRepository;
import br.com.reaproveita.service.HistoricalFoodAnalysisClient;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalFoodAnalysisClientTests {

    private Donation donation(
            String title,
            FoodCategory category,
            DonationStatus status) {

        Donation donation = mock(Donation.class);

        when(donation.getTitle()).thenReturn(title);
        when(donation.getCategory()).thenReturn(category);
        when(donation.getStatus()).thenReturn(status);

        return donation;
    }

    // ==========================================
    // TESTE 1 - Classificacao de paes
    // ==========================================

    @Test
    void shouldSuggestBakeryFromHistoricalDonations() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation bakery = donation(
            "Paes frescos do dia",
            FoodCategory.BAKERY,
            DonationStatus.AVAILABLE
        );

        Donation fruits = donation(
            "Frutas maduras",
            FoodCategory.FRUITS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(bakery, fruits)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 12,5 kg de paes frescos para doar"
        );

        assertEquals(
            FoodCategory.BAKERY,
            result.category()
        );

        assertEquals(
            0,
            new BigDecimal("12.5").compareTo(
                result.quantityKg()
            )
        );

        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 2 - Historico vazio
    // ==========================================

    @Test
    void shouldHandleEmptyHistory() {

        DonationRepository repository =
            mock(DonationRepository.class);

        when(repository.findAll()).thenReturn(List.of());

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 5 kg de bananas"
        );

        assertEquals(
            FoodCategory.OTHER,
            result.category()
        );

        assertNotNull(result.explanation());
        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 3 - Nao inventar quantidade
    // ==========================================

    @Test
    void shouldNotInventQuantity() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation grains = donation(
            "Arroz branco",
            FoodCategory.GRAINS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(grains)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho arroz branco para doacao"
        );

        assertEquals(
            FoodCategory.GRAINS,
            result.category()
        );

        assertNull(result.quantityKg());
        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 4 - Ignorar doacoes canceladas
    // ==========================================

    @Test
    void shouldIgnoreCancelledDonations() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation cancelled = donation(
            "Paes frescos",
            FoodCategory.BAKERY,
            DonationStatus.CANCELLED
        );

        when(repository.findAll()).thenReturn(
            List.of(cancelled)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 8 kg de paes frescos"
        );

        assertEquals(
            FoodCategory.OTHER,
            result.category()
        );

        assertTrue(
            result.explanation().contains(
                "Nenhuma doacao semelhante"
            )
        );
    }

    // ==========================================
    // TESTE 5 - Reconhecer singular e plural
    // ==========================================

    @Test
    void shouldRecognizeSingularAndPlural() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation bananas = donation(
            "DEMO - Bananas maduras",
            FoodCategory.FRUITS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(bananas)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 4 kg de banana madura"
        );

        assertEquals(
            FoodCategory.FRUITS,
            result.category()
        );

        assertEquals(
            0,
            new BigDecimal("4").compareTo(
                result.quantityKg()
            )
        );

        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 6 - Descricao ambigua
    // ==========================================

    @Test
    void shouldRejectAmbiguousCategories() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation bakery = donation(
            "Paes frescos",
            FoodCategory.BAKERY,
            DonationStatus.AVAILABLE
        );

        Donation fruits = donation(
            "Bananas maduras",
            FoodCategory.FRUITS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(bakery, fruits)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 8 kg de paes e bananas"
        );

        assertEquals(
            FoodCategory.OTHER,
            result.category()
        );

        assertTrue(
            result.explanation().contains("ambigua")
        );

        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 7 - Alimento desconhecido
    // ==========================================

    @Test
    void shouldNotClassifyUnknownFood() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation grains = donation(
            "Arroz branco",
            FoodCategory.GRAINS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(grains)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 3 kg de chocolate"
        );

        assertEquals(
            FoodCategory.OTHER,
            result.category()
        );

        assertTrue(
            result.explanation().contains(
                "Nenhuma doacao semelhante"
            )
        );

        assertTrue(result.requiresReview());
    }

    // ==========================================
    // TESTE 8 - Explicar correspondencias
    // ==========================================

    @Test
    void shouldExplainMatchingTerms() {

        DonationRepository repository =
            mock(DonationRepository.class);

        Donation grains = donation(
            "DEMO - Arroz branco embalado",
            FoodCategory.GRAINS,
            DonationStatus.AVAILABLE
        );

        when(repository.findAll()).thenReturn(
            List.of(grains)
        );

        var client = new HistoricalFoodAnalysisClient(repository);

        var result = client.analyze(
            "Tenho 20 kg de arroz branco"
        );

        assertEquals(
            FoodCategory.GRAINS,
            result.category()
        );

        assertNotNull(result.explanation());

        assertTrue(
            result.explanation().contains("arroz")
        );

        assertTrue(result.requiresReview());
    }
}
