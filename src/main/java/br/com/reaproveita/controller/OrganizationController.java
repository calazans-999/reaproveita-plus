package br.com.reaproveita.controller;

import br.com.reaproveita.dto.*;
import br.com.reaproveita.service.OrganizationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationService service;
    public OrganizationController(OrganizationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationCreateRequest request,
                                                        Authentication auth) {
        var result = service.create(request, auth);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + result.id())).body(result);
    }

    @GetMapping
    public List<OrganizationResponse> list(Authentication auth) { return service.list(auth); }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth);
    }

    @PutMapping("/{id}")
    public OrganizationResponse update(@PathVariable Long id,
                                       @Valid @RequestBody OrganizationUpdateRequest request,
                                       Authentication auth) {
        return service.update(id, request, auth);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth);
        return ResponseEntity.noContent().build();
    }
}
