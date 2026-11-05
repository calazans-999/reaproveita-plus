package br.com.reaproveita.controller;

import br.com.reaproveita.dto.RegisterRequest;
import br.com.reaproveita.dto.UserResponse;
import br.com.reaproveita.repository.UserRepository;
import br.com.reaproveita.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService service;
    private final UserRepository users;

    public AuthController(UserService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    // HTTP Basic autentica cada requisicao. Nao existe token nesta etapa.
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return UserResponse.from(user);
    }
}
