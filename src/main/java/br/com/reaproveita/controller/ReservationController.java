package br.com.reaproveita.controller;

import br.com.reaproveita.dto.ReservationCreateRequest;
import br.com.reaproveita.dto.ReservationResponse;
import br.com.reaproveita.service.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping("/donations/{donationId}/reservations")
    public ResponseEntity<ReservationResponse> request(@PathVariable Long donationId,
            @Valid @RequestBody ReservationCreateRequest body, Authentication auth) {
        var response = service.request(donationId, body, auth);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + response.id())).body(response);
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth);
    }

    @PatchMapping("/reservations/{id}/confirm")
    public ReservationResponse confirm(@PathVariable Long id, Authentication auth) {
        return service.confirm(id, auth);
    }

    @PatchMapping("/reservations/{id}/collect")
    public ReservationResponse collect(@PathVariable Long id, Authentication auth) {
        return service.collect(id, auth);
    }

    @PatchMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, Authentication auth) {
        return service.cancel(id, auth);
    }
}