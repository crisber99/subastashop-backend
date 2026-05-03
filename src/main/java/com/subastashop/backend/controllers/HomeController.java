package com.subastashop.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> home() {
        return ResponseEntity.ok(Map.of(
            "status", "online",
            "message", "SubastaShop API Backend is running.",
            "hint", "This is the backend server. If you are trying to access the website, please verify your Cloudflare DNS settings are pointing to your frontend hosting, not this backend server."
        ));
    }
}
