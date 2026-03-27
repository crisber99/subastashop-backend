package com.subastashop.backend.services;

import com.subastashop.backend.dtos.CalificacionRequestDTO;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Calificacion;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.CalificacionRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;
    private final ProductoRepository productoRepository;
    private final AppUserRepository userRepository;
    private final com.subastashop.backend.repositories.OrdenRepository ordenRepository;

    public CalificacionService(CalificacionRepository calificacionRepository,
                               ProductoRepository productoRepository,
                               AppUserRepository userRepository,
                               com.subastashop.backend.repositories.OrdenRepository ordenRepository) {
        this.calificacionRepository = calificacionRepository;
        this.productoRepository = productoRepository;
        this.userRepository = userRepository;
        this.ordenRepository = ordenRepository;
    }

    public List<Calificacion> obtenerPorProducto(Long productoId) {
        return calificacionRepository.findByProductoId(productoId);
    }

    public Calificacion crearCalificacion(CalificacionRequestDTO requestDto, String userEmail) {
        AppUsers usuario = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con email: " + userEmail));
        
        Producto producto = productoRepository.findById(requestDto.productoId())
                .orElseThrow(() -> new NoSuchElementException("Producto no encontrado con id: " + requestDto.productoId()));

        // --- VALIDACIÓN ANTI-SPAM ---
        // Verificar que el usuario tenga una orden completada para este producto
        boolean comproProducto = ordenRepository.hasUserBoughtProduct(userEmail, requestDto.productoId());
        
        if (!comproProducto) {
            throw new RuntimeException("No puedes calificar un producto que no has comprado.");
        }

        Calificacion calificacion = new Calificacion();
        calificacion.setProducto(producto);
        calificacion.setUsuario(usuario);
        calificacion.setPuntuacion(requestDto.puntuacion());
        calificacion.setComentario(requestDto.comentario());

        return calificacionRepository.save(calificacion);
    }
}
