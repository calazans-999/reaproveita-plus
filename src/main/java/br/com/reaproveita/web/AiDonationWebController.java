
package br.com.reaproveita.web;

import br.com.reaproveita.dto.AiFoodAnalysisRequest;
import br.com.reaproveita.dto.AiFoodAnalysisResponse;
import br.com.reaproveita.service.AiFoodAnalysisService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/donations/ai")
public class AiDonationWebController {

    private final ObjectProvider<AiFoodAnalysisService> serviceProvider;

    public AiDonationWebController(
            ObjectProvider<AiFoodAnalysisService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @PostMapping("/analyze")
    public AiFoodAnalysisResponse analyze(
            @Valid @RequestBody AiFoodAnalysisRequest request,
            Authentication authentication) {

        // A interface web utiliza autenticacao por sessao.
        if (authentication == null ||
            !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Autenticacao obrigatoria"
            );
        }

        // Somente doadores e administradores podem usar a IA.
        boolean authorized = authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority ->
                authority.getAuthority().equals("ROLE_DONOR") ||
                authority.getAuthority().equals("ROLE_ADMIN")
            );

        if (!authorized) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Acesso nao autorizado"
            );
        }

        // A IA pode permanecer desativada sem afetar o site.
        AiFoodAnalysisService service =
            serviceProvider.getIfAvailable();

        if (service == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analise por IA indisponivel"
            );
        }

        return service.analyze(request);
    }

    // Resposta controlada em caso de falha do modelo.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleAiFailure(
            IllegalStateException exception) {

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "AI_SERVICE_UNAVAILABLE",
                "message",
                "Nao foi possivel analisar o alimento. "
                + "Tente novamente mais tarde."
            ));
    }
}
