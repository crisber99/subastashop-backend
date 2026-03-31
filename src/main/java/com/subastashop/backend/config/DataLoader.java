package com.subastashop.backend.config;

import com.subastashop.backend.models.Categoria;
import com.subastashop.backend.repositories.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DataLoader: Se ejecuta al iniciar el backend.
 * Inserta categorías faltantes sin duplicar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;
    private final com.subastashop.backend.repositories.ProductoRepository productoRepository;

    // Categorías faltantes: slug -> {nombre, icono}
    private static final List<Map<String, String>> CATEGORIAS_NUEVAS = List.of(
        Map.of("slug", "juguetes-peluches", "nombre", "Juguetes/Peluches", "icono", "bi-emoji-laughing")
    );

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) {
        // 1. Verificar Categorías
        for (Map<String, String> cat : CATEGORIAS_NUEVAS) {
            String slug = cat.get("slug");
            boolean existe = categoriaRepository.findAll().stream()
                    .anyMatch(c -> c.getSlug().equalsIgnoreCase(slug));

            if (!existe) {
                Categoria nueva = new Categoria();
                nueva.setNombre(cat.get("nombre"));
                nueva.setSlug(slug);
                nueva.setIcono(cat.get("icono"));
                categoriaRepository.save(nueva);
                log.info("✅ Categoría creada: {}", cat.get("nombre"));
            }
        }

        // 2. Generar Slugs para productos existentes si no tienen
        productoRepository.findAll().forEach(p -> {
            if (p.getSlug() == null || p.getSlug().isEmpty()) {
                // Forzamos la generación llamando al método interno del modelo (o dejando que JPA lo haga)
                // Al estar en una transacción y llamar a save, el @PrePersist/@PreUpdate actuará.
                productoRepository.save(p);
                log.info("ℹ️ Slug generado para producto ID: {}", p.getId());
            }
        });
    }
}
