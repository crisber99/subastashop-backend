package com.subastashop.backend.services;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Lazy;
import com.subastashop.backend.services.SubastaSniperService;

@Service
public class SubastaService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PujaRepository pujaRepository;

    @Autowired
    private com.subastashop.backend.repositories.AppUserRepository userRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    @Lazy
    private SubastaSniperService sniperService;

    @Transactional // <--- ¡Vital! O todo o nada.
    public Puja realizarPuja(Integer productoId, Integer usuarioId, BigDecimal montoOferta) {
        return realizarPuja(productoId, usuarioId, montoOferta, false);
    }

    @Transactional
    public Puja realizarPuja(Integer productoId, Integer usuarioId, BigDecimal montoOferta, boolean isAutoBid) {
        String tenantId = TenantContext.getTenantId();

        // 1. Buscar el producto (Asegurando que sea del tenant actual)
        Producto producto = productoRepository.findByIdAndTenantId(productoId, tenantId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado o no pertenece a esta tienda"));

        // 2. Validaciones de Negocio
        if (!"SUBASTA".equalsIgnoreCase(producto.getTipoVenta())) {
             throw new RuntimeException("Este producto no es de tipo subasta");
        }

        String estado = producto.getEstado();
        if (!"EN_SUBASTA".equalsIgnoreCase(estado) && !"SUBASTA".equalsIgnoreCase(estado)) {
            throw new RuntimeException("No se puede pujar: La subasta no está activa (Estado: " + estado + ")");
        }

        // --- NUEVA VALIDACIÓN: Early Access (PRO) ---
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicioOficial = producto.getFechaInicioSubasta() != null ? 
                                     producto.getFechaInicioSubasta() : producto.getFechaCreacion();
        
        if (ahora.isBefore(inicioOficial)) {
            // Es periodo de acceso anticipado o aún no empieza
            com.subastashop.backend.models.AppUsers usuario = userRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            boolean esPro = usuario.isSuscripcionActiva() || usuario.isPagoAutomatico();
            int horasAnticipo = producto.getHorasVentaAnticipada() != null ? producto.getHorasVentaAnticipada() : 24;
            LocalDateTime inicioPro = inicioOficial.minusHours(horasAnticipo);

            if (ahora.isBefore(inicioPro)) {
                throw new RuntimeException("La subasta aún no ha comenzado para nadie.");
            }

            if (!esPro && ahora.isBefore(inicioOficial)) {
                throw new RuntimeException("Acceso Anticipado: Esta subasta solo está abierta para usuarios PRO en este momento. Abre para el público general el " + inicioOficial);
            }
        }

        if (producto.getFechaFinSubasta() != null && ahora.isAfter(producto.getFechaFinSubasta())) {
            throw new RuntimeException("La subasta ya finalizó");
        }

        // La oferta debe ser mayor al precio actual (o al base si es la primera)
        BigDecimal precioGanadorActual = producto.getPrecioActual() != null ? 
                                         producto.getPrecioActual() : producto.getPrecioBase();

        if (montoOferta.compareTo(precioGanadorActual) <= 0) {
            throw new RuntimeException("Tu oferta debe ser mayor a $" + precioGanadorActual);
        }

        // 3. Buscar al postor anterior para notificarle que lo superaron
        java.util.List<Puja> pujasAnteriores = pujaRepository.findByProductoIdOrderByMontoDesc(productoId);
        Puja pujaAnteriorMax = pujasAnteriores.isEmpty() ? null : pujasAnteriores.get(0);

        // 4. Registrar la Puja
        Puja nuevaPuja = new Puja();
        nuevaPuja.setProducto(producto);
        nuevaPuja.setUsuarioId(usuarioId);
        nuevaPuja.setMonto(montoOferta);
        // TenantId se setea solo gracias a BaseEntity

        pujaRepository.save(nuevaPuja);

        // 5. Actualizar el precio del producto en tiempo real
        producto.setPrecioActual(montoOferta);
        productoRepository.save(producto);

        // 6. Notificación Global Privada: Avisar al postor anterior si alguien más lo superó
        if (pujaAnteriorMax != null && !pujaAnteriorMax.getUsuarioId().equals(usuarioId)) {
            java.util.Map<String, Object> notificacionOutbid = new java.util.HashMap<>();
            notificacionOutbid.put("tipo", "OUTBID");
            notificacionOutbid.put("productoId", producto.getId());
            notificacionOutbid.put("productoNombre", producto.getNombre());
            notificacionOutbid.put("nuevoPrecio", montoOferta);
            
            // Enviamos el mensaje al canal privado de ese usuario en particular
            messagingTemplate.convertAndSend("/topic/usuario/" + pujaAnteriorMax.getUsuarioId(), notificacionOutbid);
        }

        // 7. Si es una puja manual, disparar los Sniper Bots
        if (!isAutoBid) {
            sniperService.procesarSnipers(productoId, usuarioId);
        }

        return nuevaPuja;
    }
}