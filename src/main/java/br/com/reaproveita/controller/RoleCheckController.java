package br.com.reaproveita.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RoleCheckController {
    @GetMapping("/admin/ping")
    public Map<String, String> admin() { return Map.of("access", "ADMIN_OK"); }

    @GetMapping("/donor/ping")
    public Map<String, String> donor() { return Map.of("access", "DONOR_OK"); }

    @GetMapping("/receiver/ping")
    public Map<String, String> receiver() { return Map.of("access", "RECEIVER_OK"); }
}
