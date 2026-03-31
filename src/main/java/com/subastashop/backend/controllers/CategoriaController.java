package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Categoria;
import com.subastashop.backend.repositories.CategoriaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    @Cacheable("categorias")
    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    @PostMapping
    @CacheEvict(value = "categorias", allEntries = true)
    public ResponseEntity<?> crearCategoria(@RequestBody Map<String, String> body) {
        try {
            String nombre = body.get("nombre");
            String slug = body.get("slug");
            String icono = body.getOrDefault("icono", "bi-tag");

            if (nombre == null || slug == null) {
                return ResponseEntity.badRequest().body("nombre y slug son requeridos");
            }

            Optional<Categoria> existing = categoriaRepository.findAll().stream()
                    .filter(c -> c.getSlug().equalsIgnoreCase(slug))
                    .findFirst();
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get()); // Devuelve la existente sin duplicar
            }

            Categoria cat = new Categoria();
            cat.setNombre(nombre);
            cat.setSlug(slug);
            cat.setIcono(icono);
            categoriaRepository.save(cat);
            return ResponseEntity.ok(cat);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
