
package br.com.reaproveita.service;

import br.com.reaproveita.dto.AiFoodAnalysisRequest;
import br.com.reaproveita.dto.AiFoodAnalysisResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "reaproveita.analysis.mode",
    havingValue = "history",
    matchIfMissing = true
)
public class AiFoodAnalysisService {

    private final AiModelClient client;

    public AiFoodAnalysisService(AiModelClient client) {
        this.client = client;
    }

    public AiFoodAnalysisResponse analyze(
            AiFoodAnalysisRequest request) {

        if (request == null ||
            request.description() == null) {

            throw new IllegalArgumentException(
                "Descricao obrigatoria"
            );
        }

        String description = request.description().trim();

        if (description.length() < 5 ||
            description.length() > 1000) {

            throw new IllegalArgumentException(
                "Descricao deve ter entre 5 e 1000 caracteres"
            );
        }

        AiFoodAnalysisResponse result =
            client.analyze(description);

        if (result == null ||
            result.category() == null) {

            throw new IllegalStateException(
                "Resposta invalida da analise historica"
            );
        }

        if (result.quantityKg() != null &&
            result.quantityKg().signum() <= 0) {

            throw new IllegalStateException(
                "Quantidade invalida retornada pela analise"
            );
        }

        return new AiFoodAnalysisResponse(
            result.category(),
            result.quantityKg(),
            result.storageCondition(),
            result.explanation(),
            true
        );
    }
}
