package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.TiendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private TiendaRepository tiendaRepository;

    // Endpoint para la Landing Page
    @GetMapping("/tiendas")
    public List<Tienda> obtenerTiendasActivas() {
        // Asumiendo que agregaste 'activa' en tu modelo, si no, usa findAll()
        return tiendaRepository.findAll(); 
    }
}