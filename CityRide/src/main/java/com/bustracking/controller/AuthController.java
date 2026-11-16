package com.bustracking.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/me")
    public Map<String, String> currentUser(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst().orElse("ROLE_USER").replace("ROLE_", "");
        return Map.of("username", authentication.getName(), "role", role);
    }
}
