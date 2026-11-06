package br.com.reaproveita.controller;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.model.FoodCategory;
import br.com.reaproveita.service.DonationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/donations")
public class DonationController {
    private final DonationService service;
    public DonationController(DonationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DonationResponse> create(@Valid @RequestBody DonationCreateRequest r,
                                                    Authentication auth) {
        var response = service.create(r, auth);
        return ResponseEntity.created(URI.create("/api/v1/donations/" + response.id())).body(response);
    }

    @GetMapping
    public List<DonationResponse> list(@RequestParam(required = false) FoodCategory category,
                                       Authentication auth) {
        return service.list(category, auth);
    }

    @GetMapping("/{id}")
    public DonationResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth);
    }

    @PutMapping("/{id}")
    public DonationResponse update(@PathVariable Long id, @Valid @RequestBody DonationUpdateRequest r,
                                   Authentication auth) {
        return service.update(id, r, auth);
    }

    @PatchMapping("/{id}/cancel")
    public DonationResponse cancel(@PathVariable Long id, Authentication auth) {
        return service.cancel(id, auth);
    }
}
