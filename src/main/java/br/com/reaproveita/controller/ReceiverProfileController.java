package br.com.reaproveita.controller;

import br.com.reaproveita.dto.ReceiverProfileRequest;
import br.com.reaproveita.dto.ReceiverProfileResponse;
import br.com.reaproveita.service.ReceiverProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receiver-profiles")
public class ReceiverProfileController {
    private final ReceiverProfileService service;
    public ReceiverProfileController(ReceiverProfileService service) { this.service = service; }

    @PutMapping("/{organizationId}")
    public ResponseEntity<ReceiverProfileResponse> upsert(@PathVariable Long organizationId,
            @Valid @RequestBody ReceiverProfileRequest request, Authentication auth) {
        return ResponseEntity.ok(service.upsert(organizationId, request, auth));
    }

    @GetMapping("/{organizationId}")
    public ReceiverProfileResponse get(@PathVariable Long organizationId, Authentication auth) {
        return service.get(organizationId, auth);
    }
}
