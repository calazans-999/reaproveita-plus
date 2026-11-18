
package br.com.reaproveita.controller;

import br.com.reaproveita.dto.AiFoodAnalysisRequest;
import br.com.reaproveita.dto.AiFoodAnalysisResponse;
import br.com.reaproveita.service.AiFoodAnalysisService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiFoodAnalysisController {

    private final ObjectProvider<AiFoodAnalysisService> serviceProvider;

    public AiFoodAnalysisController(
            ObjectProvider<AiFoodAnalysisService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @PostMapping("/analyze-donation")
    public AiFoodAnalysisResponse analyze(
            @Valid @RequestBody AiFoodAnalysisRequest request) {

        AiFoodAnalysisService service =
            serviceProvider.getIfAvailable();

        if (service == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analise por IA desativada"
            );
        }

        return service.analyze(request);
    }

    // Trata falhas conhecidas do servico/modelo de IA.
    // Nunca devolve a chave de API, prompt ou resposta bruta.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleAiFailure(
            IllegalStateException exception) {

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "AI_SERVICE_UNAVAILABLE",
                "message",
                "Nao foi possivel concluir a analise. "
                + "Tente novamente mais tarde."
            ));
    }
}
