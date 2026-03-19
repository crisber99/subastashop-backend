package com.subastashop.backend.services;

import com.subastashop.backend.dto.DetalleRequest;
import com.subastashop.backend.dto.OrdenRequest;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.DetalleOrden;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.DetalleOrdenRepository;
import com.subastashop.backend.repositories.OrdenRepository;
import com.subastashop.backend.repositories.ProductoRepository;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrdenService {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ProductoRepository productoRepo;

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private DetalleOrdenRepository detalleOrdenRepository;

    @Autowired
    private com.subastashop.backend.services.EmailService emailService;

    @Transactional
    public String pagarOrden(Integer id) {
        Orden orden = ordenRepository.findByIdConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if ("PAGADO".equals(orden.getEstado())) {
            throw new RuntimeException("Esta orden ya está pagada.");
        }

        orden.setEstado("PAGADO");

        // MARCAR PRODUCTOS COMO VENDIDOS
        for (DetalleOrden detalle : orden.getDetalles()) {
            Producto p = detalle.getProducto();
            p.setEstado("VENDIDO");
            productoRepo.save(p);
        }

        ordenRepository.save(orden);

        // Notificación de Pago Exitoso
        try {
            String destino = orden.getUsuario().getEmail();
            String asunto = "Pago Exitoso - Orden #" + orden.getId();
            String mensaje = "Hola " + orden.getUsuario().getNombreCompleto() + ",<br><br>" +
                             "Hemos recibido el pago de tu orden #" + orden.getId() + " por un total de $" + orden.getTotal() + ".<br><br>" +
                             "¡Gracias por tu compra en SubastaShop!<br><br>" +
                             "Saludos,<br>El equipo de SubastaShop";
            emailService.enviarCorreo(destino, asunto, mensaje);
        } catch (Exception e) {
            // Ignorar para no interrumpir el flujo de la orden
        }

        return "Pago exitoso. ¡Producto en camino!";
    }

    @Transactional
    public Orden crearOrden(OrdenRequest request, String email) {
        AppUsers usuarioActual = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getDetalles().isEmpty()) {
            throw new RuntimeException("El carrito no puede estar vacío");
        }

        Long primerProductoId = request.getDetalles().get(0).getProductoId();
        Producto primerProducto = productoRepo.findById(primerProductoId.intValue())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        
        Tienda tiendaOrden = primerProducto.getTienda();

        Orden orden = new Orden();
        orden.setUsuario(usuarioActual);
        orden.setTienda(tiendaOrden);
        orden.setFechaCreacion(LocalDateTime.now());
        orden.setFechaExpiracionReserva(LocalDateTime.now().plusHours(24)); 
        orden.setEstado("PENDIENTE_PAGO");
        orden.setTotal(BigDecimal.ZERO);
        
        orden = ordenRepository.save(orden); 

        BigDecimal totalOrden = BigDecimal.ZERO; 
        List<DetalleOrden> detallesGuardados = new ArrayList<>();

        for (DetalleRequest d : request.getDetalles()) {
            Integer idProducto = d.getProductoId().intValue();
            Producto p = productoRepo.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + idProducto));
            
            BigDecimal precioAUsar = BigDecimal.ZERO;

            if ("DIRECTA".equals(d.getTipoCompra())) {
                if (!"DISPONIBLE".equals(p.getEstado())) {
                    throw new RuntimeException("El producto " + p.getNombre() + " ya no está disponible.");
                }
                p.setEstado("RESERVADO");
                productoRepo.save(p);
                precioAUsar = p.getPrecioBase();
            } else if ("RIFA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioTicket();
            } else if ("SUBASTA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioActual();
            }
            
            totalOrden = totalOrden.add(precioAUsar);

            DetalleOrden detalle = new DetalleOrden();
            detalle.setOrden(orden);
            detalle.setProducto(p);
            detalle.setCantidad(d.getCantidad());
            detalle.setTipoCompra(d.getTipoCompra());
            detalle.setDatosExtra(d.getDatosExtra());
            detalle.setPrecioUnitario(precioAUsar);

            detalleOrdenRepository.save(detalle);
            detallesGuardados.add(detalle);
        }

        orden.setTotal(totalOrden);
        
        if (!detallesGuardados.isEmpty()) {
            orden.setTienda(detallesGuardados.get(0).getProducto().getTienda());
        }
        
        return ordenRepository.save(orden);
    }

    @Transactional
    public List<Orden> obtenerMisOrdenes(String email) {
        AppUsers usuario = usuarioRepository.findByEmail(email).orElseThrow();
        List<Orden> ordenes = ordenRepository.findByUsuarioOrderByIdDesc(usuario);
        // Forzamos carga de detalles si fuera necesario (aunque ahora es EAGER)
        ordenes.forEach(o -> o.getDetalles().size());
        return ordenes;
    }

    @Transactional
    public Orden obtenerOrdenPorId(Integer id, String email) {
        Orden orden = ordenRepository.findByIdConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (!orden.getUsuario().getEmail().equals(email)) {
             throw new RuntimeException("No tienes permiso para ver esta orden");
        }

        return orden;
    }
}
