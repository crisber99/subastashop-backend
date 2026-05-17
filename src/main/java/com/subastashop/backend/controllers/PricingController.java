package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final AppUserRepository userRepository;

    public PricingController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPricingStatus() {
        long totalProUsers = userRepository.countByRol(Role.ROLE_ADMIN);
        
        int faseActual = 1;
        int precioActual = 2490;
        int cuposTotalesFase = 100;
        long cuposOcupadosFase = totalProUsers;
        long cuposRestantes = 100 - totalProUsers;
        int precioAncla = 9990;

        if (totalProUsers >= 100 && totalProUsers < 500) {
            faseActual = 2;
            precioActual = 4990;
            cuposTotalesFase = 400; // de 101 a 500 hay 400 cupos
            cuposOcupadosFase = totalProUsers - 100;
            cuposRestantes = 500 - totalProUsers;
        } else if (totalProUsers >= 500) {
            faseActual = 3;
            precioActual = 6990;
            cuposTotalesFase = -1; // infinito
            cuposOcupadosFase = totalProUsers - 500;
            cuposRestantes = 0;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("faseActual", faseActual);
        response.put("precioActual", precioActual);
        response.put("precioAncla", precioAncla);
        response.put("cuposTotalesFase", cuposTotalesFase);
        response.put("cuposOcupadosFase", cuposOcupadosFase);
        response.put("cuposRestantes", Math.max(0, cuposRestantes));
        response.put("totalProUsers", totalProUsers);

        return ResponseEntity.ok(response);
    }
}
