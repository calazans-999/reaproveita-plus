
package br.com.reaproveita.service;

import br.com.reaproveita.dto.AiFoodAnalysisResponse;
import br.com.reaproveita.model.DonationStatus;
import br.com.reaproveita.model.FoodCategory;
import br.com.reaproveita.repository.DonationRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(
    name = "reaproveita.analysis.mode",
    havingValue = "history",
    matchIfMissing = true
)
public class HistoricalFoodAnalysisClient implements AiModelClient {

    private static final Pattern QUANTITY = Pattern.compile(
        "(?<!\\d)(\\d+(?:[.,]\\d+)?)\\s*(?:kg|quilos?)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> IGNORED_WORDS = Set.of(
        "demo", "tenho", "temos", "quero", "gostaria",
        "doar", "doacao", "disponivel", "disponiveis",
        "produto", "produtos", "alimento", "alimentos",
        "para", "com", "sem", "uma", "umas", "uns",
        "dos", "das", "que", "hoje", "amanha",
        "quilo", "quilos", "fresco", "fresca",
        "maduro", "madura", "branco", "branca",
        "embalado", "embalada", "lacrado", "lacrada",
        "integral", "integrai"
    );

    private final DonationRepository donations;

    public HistoricalFoodAnalysisClient(
            DonationRepository donations) {

        this.donations = donations;
    }

    @Override
    public AiFoodAnalysisResponse analyze(String description) {

        Set<String> queryWords = words(description);
        BigDecimal quantity = extractQuantity(description);

        List<Match> matches = donations.findAll()
            .stream()
            .filter(d -> d.getStatus() != DonationStatus.CANCELLED)
            .filter(d -> d.getStatus() != DonationStatus.BLOCKED)
            .filter(d -> d.getCategory() != null)
            .filter(d -> d.getTitle() != null)
            .filter(d -> !d.getTitle().isBlank())
            .map(d -> new Match(
                d.getCategory(),
                commonWords(
                    queryWords,
                    words(d.getTitle())
                )
            ))
            .filter(m -> !m.terms().isEmpty())
            .toList();

        if (matches.isEmpty()) {
            return response(
                FoodCategory.OTHER,
                quantity,
                "Nenhuma doacao semelhante foi encontrada "
                + "no historico. Selecione e revise "
                + "a categoria manualmente."
            );
        }

        // Usamos a melhor correspondencia por categoria,
        // evitando que varias doacoes repetidas distorcam
        // a classificacao.

        Map<FoodCategory, Integer> scores =
            new EnumMap<>(FoodCategory.class);

        for (Match match : matches) {
            scores.merge(
                match.category(),
                match.terms().size(),
                Math::max
            );
        }

        int bestScore = scores.values()
            .stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        List<FoodCategory> bestCategories = scores.entrySet()
            .stream()
            .filter(entry -> entry.getValue() == bestScore)
            .map(Map.Entry::getKey)
            .toList();

        if (bestCategories.size() != 1) {
            return response(
                FoodCategory.OTHER,
                quantity,
                "Descricao ambigua: foram encontradas "
                + "correspondencias igualmente fortes "
                + "em categorias diferentes. "
                + "Revise a categoria manualmente."
            );
        }

        FoodCategory bestCategory = bestCategories.get(0);

        int secondScore = scores.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != bestCategory)
            .mapToInt(Map.Entry::getValue)
            .max()
            .orElse(0);

        // Se outra categoria tiver pontuacao proxima,
        // preferimos solicitar revisao manual.
        if (secondScore > 0 &&
            secondScore * 2 >= bestScore) {

            return response(
                FoodCategory.OTHER,
                quantity,
                "Descricao ambigua: existem alimentos "
                + "semelhantes em mais de uma categoria. "
                + "Revise a categoria manualmente."
            );
        }

        List<Match> categoryMatches = matches.stream()
            .filter(m -> m.category() == bestCategory)
            .toList();

        String matchedTerms = categoryMatches.stream()
            .flatMap(m -> m.terms().stream())
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));

        String explanation =
            "Categoria sugerida com base em "
            + categoryMatches.size()
            + " doacao(oes) semelhante(s) no historico. "
            + "Termos encontrados: "
            + matchedTerms
            + ". Revise os dados antes de publicar.";

        return response(
            bestCategory,
            quantity,
            explanation
        );
    }

    private AiFoodAnalysisResponse response(
            FoodCategory category,
            BigDecimal quantity,
            String explanation) {

        return new AiFoodAnalysisResponse(
            category,
            quantity,
            null,
            explanation,
            true
        );
    }

    private BigDecimal extractQuantity(String description) {

        var matcher = QUANTITY.matcher(description);

        if (!matcher.find()) {
            return null;
        }

        BigDecimal quantity = new BigDecimal(
            matcher.group(1).replace(',', '.')
        );

        return quantity.signum() > 0 ? quantity : null;
    }

    private Set<String> commonWords(
            Set<String> first,
            Set<String> second) {

        return first.stream()
            .filter(second::contains)
            .collect(Collectors.toSet());
    }

    private Set<String> words(String text) {

        String normalized = Normalizer.normalize(
            text.toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "");

        return Arrays.stream(
                normalized.split("[^a-z]+")
            )
            .map(this::canonicalWord)
            .filter(word -> word.length() >= 3)
            .filter(word -> !IGNORED_WORDS.contains(word))
            .collect(Collectors.toSet());
    }

    private String canonicalWord(String word) {

        // Casos comuns com plural irregular.
        if (word.equals("paes") ||
            word.equals("paos") ||
            word.equals("pao")) {
            return "pao";
        }

        // Simplificacao para plurais regulares.
        if (word.length() > 4 && word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }

    private record Match(
        FoodCategory category,
        Set<String> terms
    ) {}
}
