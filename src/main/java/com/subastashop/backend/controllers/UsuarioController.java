package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.OrdenRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.subastashop.backend.models.Role;

import java.util.List;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    @Autowired
    private PujaRepository pujaRepository;
    @Autowired
    private OrdenRepository ordenRepository;
    @Autowired
    private AppUserRepository usuarioRepository;

    // 1. Endpoint para "Mis Pujas" 🔨
    @GetMapping("/mis-pujas")
    public ResponseEntity<List<Puja>> obtenerMisPujas() {
        AppUsers usuario = getUsuarioAutenticado();
        // Devuelve el objeto PUJA completo (que incluye el Producto dentro)
        return ResponseEntity.ok(pujaRepository.findByUsuarioIdOrderByFechaPujaDesc(usuario.getId()));
    }

    // 2. Endpoint para "Mis Compras/Ganadas" 🏆
    @GetMapping("/mis-compras")
    public ResponseEntity<List<Orden>> obtenerMisCompras() {
        AppUsers usuario = getUsuarioAutenticado();
        // Busca las ordenes de este usuario
        return ResponseEntity.ok(ordenRepository.findByUsuarioId(usuario.getId()));
    }

    // Método auxiliar para no repetir código
    private AppUsers getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @PostMapping("/convertir-vendedor")
    public ResponseEntity<?> solicitarSerVendedor() {
        // 1. Obtener usuario actual del token
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email).get();

        // 2. Cambiar Rol
        usuario.setRol(Role.ROLE_VENDEDOR); // Asegúrate de agregar VENDEDOR a tu Enum Role
        usuarioRepository.save(usuario);

        // 3. (Opcional) Aquí podrías pedir RUT, Cuenta Bancaria, etc. antes de
        // aprobarlo.

        return ResponseEntity.ok("¡Felicidades! Ahora tienes acceso al panel de ventas.");
    }
}