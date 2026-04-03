package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.CrearTiendaDTO;
import com.subastashop.backend.models.*;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
// ¡IMPORTANTE! En SecurityConfig debes proteger esto:
// .requestMatchers("/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
public class SuperAdminController {

    @Autowired
    private TiendaRepository tiendaRepository;
    @Autowired
    private AppUserRepository usuarioRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<AppUsers>> listarUsuarios() {
        // En un sistema real, aquí usarías paginación (Pageable)
        return ResponseEntity.ok(usuarioRepository.findAll());
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String nuevoRolTexto = body.get("rol"); // Esperamos json: { "rol": "ROLE_ADMIN" }

        AppUsers usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Evitar que el Super Admin se bloquee a sí mismo o se quite el rol
        if (usuario.getRol().name().equals("ROLE_SUPER_ADMIN")) {
            return ResponseEntity.badRequest().body(Map.of("error", "No puedes modificar al Super Admin Supremo."));
        }

        try {
            // 2. CORRECCIÓN DEL ROL: Convertir String -> Enum
            // Asumiendo que tu Enum se llama 'Role'
            Role rolEnum = Role.valueOf(nuevoRolTexto);
            usuario.setRol(rolEnum);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rol enviado no existe."));
        }

        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("mensaje", "Rol actualizado a: " + nuevoRolTexto));
    }

    @PostMapping("/{id}/regalar-suscripcion")
    public ResponseEntity<?> regalarSuscripcion(@PathVariable Integer id) {
        AppUsers usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setRol(Role.ROLE_ADMIN);
        usuario.setSuscripcionActiva(true);
        usuario.setPagoAutomatico(false); // Es un regalo, no hay suscripción recurrente real
        
        // Inicializar tienda si no tiene
        if (usuario.getTienda() == null) {
            Tienda tienda = new Tienda();
            tienda.setNombre("Mi Tienda SubastaShop");
            tienda.setSlug("tienda-" + usuario.getId());
            tienda.setActiva(true);
            tienda.setColorPrimario("#0d6efd");
            tienda = tiendaRepository.save(tienda);
            usuario.setTienda(tienda);
        }
        
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("mensaje", "Suscripción PRO regalada con éxito a " + usuario.getEmail()));
    }

    // 3. ELIMINAR/BLOQUEAR USUARIO
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Integer id) {
        // Lógica para borrar o desactivar (soft delete recomendado)
        usuarioRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado"));
    }

    // 4. ACTUALIZAR DATOS DE USUARIO (Nombre, teléfono, etc.)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(@PathVariable Integer id, @RequestBody AppUsers datos) {
        AppUsers usuario = usuarioRepository.findById(id).orElseThrow();
        
        if (datos.getNombreCompleto() != null) usuario.setNombreCompleto(datos.getNombreCompleto());
        if (datos.getTelefono() != null) usuario.setTelefono(datos.getTelefono());
        if (datos.getDireccion() != null) usuario.setDireccion(datos.getDireccion());
        if (datos.getEmail() != null) usuario.setEmail(datos.getEmail());
        
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario actualizado correctamente"));
    }

    // 5. ESTADÍSTICAS GLOBALES PARA DASHBOARD SUPER ADMIN
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsuarios", usuarioRepository.count());
        stats.put("totalTiendas", tiendaRepository.count());
        stats.put("totalProductos", productoRepository.count());
        stats.put("subastasActivas", productoRepository.countByEstado("SUBASTA"));
        
        return ResponseEntity.ok(stats);
    }

    // 1. VER TODAS LAS TIENDAS
    @GetMapping("/tiendas")
    public List<Tienda> listarTiendas() {
        return tiendaRepository.findAll();
    }

    // 2. CREAR UNA NUEVA TIENDA (ONBOARDING)
    @PostMapping("/tiendas")
    public ResponseEntity<?> crearTienda(@RequestBody Tienda nuevaTienda) {
        if (tiendaRepository.existsBySlug(nuevaTienda.getSlug())) { // Crear existsBySlug en Repo
            return ResponseEntity.badRequest().body(Map.of("error", "Ese slug/URL ya está ocupado."));
        }
        return ResponseEntity.ok(tiendaRepository.save(nuevaTienda));
    }

    // 3. CREAR UN ADMINISTRADOR PARA UNA TIENDA
    @PostMapping("/tiendas/{tiendaId}/admin")
    public ResponseEntity<?> crearAdminTienda(@PathVariable Long tiendaId, @RequestBody AppUsers nuevoAdmin) {
        Tienda tienda = tiendaRepository.findById(tiendaId).orElseThrow();

        nuevoAdmin.setTienda(tienda);
        nuevoAdmin.setRol(Role.ROLE_ADMIN); // Admin de ESA tienda
        nuevoAdmin.setPassword(passwordEncoder.encode(nuevoAdmin.getPassword()));

        usuarioRepository.save(nuevoAdmin);

        return ResponseEntity.ok(Map.of("mensaje", "Admin creado para la tienda: " + tienda.getNombre()));
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearTienda(@RequestBody CrearTiendaDTO dto) {

        // A. Validar que la URL (slug) no exista
        if (tiendaRepository.existsBySlug(dto.getSlug())) {
            return ResponseEntity.badRequest().body(Map.of("error", "❌ Error: La URL '" + dto.getSlug() + "' ya está ocupada."));
        }

        // B. Validar que el usuario exista (El dueño debe registrarse primero)
        AppUsers nuevoDueño = usuarioRepository.findByEmail(dto.getEmailAdmin())
                .orElseThrow(() -> new RuntimeException(
                        "❌ El usuario " + dto.getEmailAdmin() + " no existe. Pídele que se registre primero."));

        // C. Validar que el usuario NO tenga ya una tienda
        if (nuevoDueño.getTienda() != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "❌ Este usuario ya es dueño de '" + nuevoDueño.getTienda().getNombre() + "'."));
        }

        // D. Crear la Tienda
        Tienda tienda = new Tienda();
        tienda.setNombre(dto.getNombre());
        tienda.setSlug(dto.getSlug());
        tienda.setActiva(true);
        // Puedes agregar logoUrl o colores aquí si los mandas en el DTO

        Tienda tiendaGuardada = tiendaRepository.save(tienda);

        // E. Actualizar al Usuario (Darle Rol y Tienda)
        nuevoDueño.setTienda(tiendaGuardada);
        nuevoDueño.setRol(Role.ROLE_ADMIN); // Le damos poder de Admin
        usuarioRepository.save(nuevoDueño);

        return ResponseEntity.ok(Map.of("mensaje", "✅ Tienda '" + tienda.getNombre() + "' creada y asignada a " + nuevoDueño.getEmail()));
    }

    // 6. LISTAR TODOS LOS PRODUCTOS DE TODAS LAS TIENDAS
    @GetMapping("/global-productos")
    public ResponseEntity<List<Producto>> listarTodosLosProductos() {
        return ResponseEntity.ok(productoRepository.findAll());
    }
}