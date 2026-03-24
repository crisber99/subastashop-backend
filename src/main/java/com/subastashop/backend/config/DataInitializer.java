package com.subastashop.backend.config;

import com.subastashop.backend.models.Categoria;
import com.subastashop.backend.repositories.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Override
    public void run(String... args) throws Exception {
        if (categoriaRepository.count() == 0) {
            crearCategoria("Tecnología", "tecnologia", "bi-laptop");
            crearCategoria("Hogar y Muebles", "hogar", "bi-house");
            crearCategoria("Moda y Accesorios", "moda", "bi-tag");
            crearCategoria("Coleccionables", "coleccionables", "bi-gem");
            crearCategoria("Deportes", "deportes", "bi-trophy");
            crearCategoria("Servicios", "servicios", "bi-tools");
            crearCategoria("Otros", "otros", "bi-box");
            System.out.println("🌱 Categorías iniciales sembradas con éxito.");
        }
    }

    private void crearCategoria(String nombre, String slug, String icono) {
        Categoria c = new Categoria();
        c.setNombre(nombre);
        c.setSlug(slug);
        c.setIcono(icono);
        categoriaRepository.save(c);
    }
}
