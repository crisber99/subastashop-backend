package com.subastashop.backend.services;

import com.subastashop.backend.dto.CrearTiendaDTO;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.PrelaunchSubscriber;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.PrelaunchSubscriberRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TiendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SuperAdminService {

    @Autowired
    private TiendaRepository tiendaRepository;
    @Autowired
    private AppUserRepository usuarioRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PrelaunchSubscriberRepository prelaunchSubscriberRepository;

    public List<AppUsers> listarUsuarios() {
        return usuarioRepository.findAll();
    }

    public void cambiarRol(Integer id, String nuevoRolTexto) {
        AppUsers usuario = usuarioRepository.findById(Objects.requireNonNull(id, "ID no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getRol().name().equals("ROLE_SUPER_ADMIN")) {
            throw new RuntimeException("No puedes modificar al Super Admin Supremo.");
        }

        try {
            Role rolEnum = Role.valueOf(nuevoRolTexto);
            usuario.setRol(rolEnum);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("El rol enviado no existe.");
        }

        usuarioRepository.save(usuario);
    }

    public void regalarSuscripcion(Integer id) {
        AppUsers usuario = usuarioRepository.findById(Objects.requireNonNull(id, "ID no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setRol(Role.ROLE_ADMIN);
        usuario.setSuscripcionActiva(true);
        usuario.setPagoAutomatico(false);

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
    }

    public void eliminarUsuario(Integer id) {
        usuarioRepository.deleteById(Objects.requireNonNull(id, "ID no puede ser nulo"));
    }

    public void actualizarUsuario(Integer id, AppUsers datos) {
        AppUsers usuario = usuarioRepository.findById(Objects.requireNonNull(id, "ID no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (datos.getNombreCompleto() != null) usuario.setNombreCompleto(datos.getNombreCompleto());
        if (datos.getTelefono() != null) usuario.setTelefono(datos.getTelefono());
        if (datos.getDireccion() != null) usuario.setDireccion(datos.getDireccion());
        if (datos.getEmail() != null) usuario.setEmail(datos.getEmail());

        usuarioRepository.save(usuario);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsuarios", usuarioRepository.count());
        stats.put("totalTiendas", tiendaRepository.count());
        stats.put("totalProductos", productoRepository.count());
        stats.put("subastasActivas", productoRepository.countByEstado("SUBASTA"));
        return stats;
    }

    public List<Tienda> listarTiendas() {
        return tiendaRepository.findAll();
    }

    public Tienda crearTienda(Tienda nuevaTienda) {
        if (tiendaRepository.existsBySlug(nuevaTienda.getSlug())) {
            throw new RuntimeException("Ese slug/URL ya está ocupado.");
        }
        return tiendaRepository.save(nuevaTienda);
    }

    public void crearAdminTienda(Long tiendaId, AppUsers nuevoAdmin) {
        Tienda tienda = tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada"));

        nuevoAdmin.setTienda(tienda);
        nuevoAdmin.setRol(Role.ROLE_ADMIN);
        nuevoAdmin.setPassword(passwordEncoder.encode(nuevoAdmin.getPassword()));

        usuarioRepository.save(nuevoAdmin);
    }

    public void crearTiendaDTO(CrearTiendaDTO dto) {
        if (tiendaRepository.existsBySlug(dto.getSlug())) {
            throw new RuntimeException("La URL '" + dto.getSlug() + "' ya está ocupada.");
        }

        AppUsers nuevoDueño = usuarioRepository.findByEmail(dto.getEmailAdmin())
                .orElseThrow(() -> new RuntimeException("El usuario " + dto.getEmailAdmin() + " no existe. Pídele que se registre primero."));

        if (nuevoDueño.getTienda() != null) {
            throw new RuntimeException("Este usuario ya es dueño de '" + nuevoDueño.getTienda().getNombre() + "'.");
        }

        Tienda tienda = new Tienda();
        tienda.setNombre(dto.getNombre());
        tienda.setSlug(dto.getSlug());
        tienda.setActiva(true);

        Tienda tiendaGuardada = tiendaRepository.save(tienda);

        nuevoDueño.setTienda(tiendaGuardada);
        nuevoDueño.setRol(Role.ROLE_ADMIN);
        usuarioRepository.save(nuevoDueño);
    }

    public List<Producto> listarTodosLosProductos() {
        return productoRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = "tiendasActivas", allEntries = true)
    public void eliminarTienda(Long id) {
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada"));

        List<AppUsers> vinculados = usuarioRepository.findAllByTiendaId(id);
        for (AppUsers u : vinculados) {
            u.setTienda(null);
            if (u.getRol() == Role.ROLE_ADMIN) {
                u.setRol(Role.ROLE_COMPRADOR);
            }
            u.setSuscripcionActiva(false);
            usuarioRepository.save(u);
        }

        List<Producto> productos = productoRepository.findByTiendaId(id);
        productoRepository.deleteAll(productos);

        tiendaRepository.delete(tienda);
    }

    public void actualizarTienda(Long id, Map<String, String> body) {
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada"));
        if (body.containsKey("nombre")) tienda.setNombre(body.get("nombre"));
        if (body.containsKey("slug")) {
            String newSlug = body.get("slug");
            if (!newSlug.equals(tienda.getSlug()) && tiendaRepository.existsBySlug(newSlug)) {
                throw new RuntimeException("El slug ya existe");
            }
            tienda.setSlug(newSlug);
        }
        tiendaRepository.save(tienda);
    }

    public List<PrelaunchSubscriber> listarSuscriptoresLanzamiento() {
        return prelaunchSubscriberRepository.findAll();
    }
}
