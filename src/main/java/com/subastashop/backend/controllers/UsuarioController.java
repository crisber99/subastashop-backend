package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.OrdenRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    @Autowired
    private PujaRepository pujaRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    // Helper para sacar el ID del token
    private Integer getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUsers user = (AppUsers) auth.getPrincipal();
        return user.getId();
    }

    @GetMapping("/mis-pujas")
    public ResponseEntity<List<Puja>> misPujas() {
        return ResponseEntity.ok(pujaRepository.findByUsuarioIdOrderByFechaPujaDesc(getUserId()));
    }

    @GetMapping("/mis-compras")
    public ResponseEntity<List<Orden>> misCompras() {
        return ResponseEntity.ok(ordenRepository.findByUsuarioId(getUserId()));
    }
}