package br.com.reaproveita.controller;

import br.com.reaproveita.dto.RecommendationResponse;
import br.com.reaproveita.service.RecommendationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }

    @GetMapping("/donations/{id}/recommendations")
    public List<RecommendationResponse> forDonation(@PathVariable Long id, Authentication auth) {
        return service.forDonation(id, auth);
    }

    @GetMapping("/recommendations")
    public List<RecommendationResponse> forReceiver(@RequestParam Long receiverOrganizationId,
                                                      Authentication auth) {
        return service.forReceiver(receiverOrganizationId, auth);
    }
}
