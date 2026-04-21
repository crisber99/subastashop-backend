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

    @Autowired
    private com.subastashop.backend.services.StorageService storageService;

    @Transactional
    public String pagarOrden(Integer id) {
        Orden orden = ordenRepository.findByIdConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if ("PAGADO".equals(orden.getEstado())) {
            throw new RuntimeException("Esta orden ya está pagada.");
        }

        orden.setEstado("PAGADO");

        // MARCAR PRODUCTOS COMO VENDIDOS Y REVELAR CAJAS MISTERIOSAS
        List<String> premiosObtenidos = new ArrayList<>();
        for (DetalleOrden detalle : orden.getDetalles()) {
            Producto p = detalle.getProducto();
            p.setEstado("VENDIDO");
            productoRepo.save(p);

            if ("CAJA_MISTERIOSA".equals(detalle.getTipoCompra())) {
                try {
                    String premio = revelarPremioCaja(detalle);
                    premiosObtenidos.add(p.getNombre() + ": " + premio);
                } catch (Exception e) {
                    System.err.println("Error al revelar caja automáticamente: " + e.getMessage());
                }
            }
        }

        ordenRepository.save(orden);

        // Notificación de Pago Exitoso + Premios
        try {
            String destino = orden.getUsuario().getEmail();
            String asunto = "¡Pago Exitoso y Premio Revelado! - Orden #" + orden.getId();
            
            StringBuilder mensajeBuilder = new StringBuilder();
            mensajeBuilder.append("Hola ").append(orden.getUsuario().getNombreCompleto()).append(",<br><br>");
            mensajeBuilder.append("Hemos recibido el pago de tu orden <b>#").append(orden.getId()).append("</b>.<br>");
            
            if (!premiosObtenidos.isEmpty()) {
                mensajeBuilder.append("<br><b>🎁 ¡Tus premios de la Caja Misteriosa ya están aquí!</b><br>");
                for (String p : premiosObtenidos) {
                    mensajeBuilder.append("- ").append(p).append("<br>");
                }
                mensajeBuilder.append("<br>Puedes ver los detalles entrando a <b>Mis Compras</b> en tu perfil.<br>");
            } else {
                mensajeBuilder.append("Tu compra por un total de <b>$").append(orden.getTotal()).append("</b> ha sido procesada con éxito.<br>");
            }
            
            mensajeBuilder.append("<br>¡Gracias por confiar en SubastaShop!<br><br>");
            mensajeBuilder.append("Saludos,<br>El equipo de SubastaShop");

            emailService.enviarCorreo(destino, asunto, mensajeBuilder.toString());
        } catch (Exception e) {
            // Ignorar para no interrumpir el flujo
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

        BigDecimal totalOrden = BigDecimal.ZERO;
        List<DetalleOrden> detallesAProcesar = new ArrayList<>();
        Tienda tiendaOrden = null;

        // 1. Calcular total y preparar datos (Incluso para validación de estado)
        for (DetalleRequest d : request.getDetalles()) {
            Integer idProducto = d.getProductoId().intValue();
            Producto p = productoRepo.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + idProducto));

            if (tiendaOrden == null) tiendaOrden = p.getTienda();

            BigDecimal precioAUsar = BigDecimal.ZERO;
            if ("DIRECTA".equals(d.getTipoCompra()) || "CAJA_MISTERIOSA".equals(d.getTipoCompra())) {
                // Solo validamos disponibilidad si el producto NO está ya reservado por este mismo usuario
                // Pero para simplificar, permitimos que el flujo continúe si el usuario ya tiene una orden pendiente
                precioAUsar = ("DIRECTA".equals(d.getTipoCompra())) ? p.getPrecioBase() : (p.getPrecioTicket() != null ? p.getPrecioTicket() : p.getPrecioBase());
            } else if ("RIFA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioTicket();
            } else if ("SUBASTA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioActual();
            }
            totalOrden = totalOrden.add(precioAUsar);
        }

        // 2. BUSCAR SI YA EXISTE UNA ORDEN PENDIENTE (Solo para compras individuales de momento o simplificación)
        if (request.getDetalles().size() == 1) {
            Integer productoId = request.getDetalles().get(0).getProductoId().intValue();
            var existe = ordenRepository.findPendingOrderByUserAndProduct(email, productoId);
            if (existe.isPresent()) {
                Orden ordenExistente = existe.get();
                
                // ASEGURAR BLOQUEO Y TOTAL
                Producto p = productoRepo.findById(productoId).orElseThrow();
                if ("DIRECTA".equals(p.getTipoVenta()) || "CAJA_MISTERIOSA".equals(p.getTipoVenta())) {
                   p.setEstado("RESERVADO");
                   productoRepo.save(p);
                }
                
                ordenExistente.setTotal(totalOrden);
                return ordenRepository.save(ordenExistente);
            }
        }

        // 3. CREACIÓN DE ORDEN NUEVA
        Orden orden = new Orden();
        orden.setUsuario(usuarioActual);
        orden.setTienda(tiendaOrden);
        orden.setFechaCreacion(LocalDateTime.now());
        orden.setFechaExpiracionReserva(LocalDateTime.now().plusHours(24));
        orden.setEstado("PENDIENTE_PAGO");
        orden.setTotal(totalOrden);
        orden.setPreferenciaEnvio(request.getPreferenciaEnvio());

        final Orden ordenGuardada = ordenRepository.save(orden);

        for (DetalleRequest d : request.getDetalles()) {
            Integer idProducto = d.getProductoId().intValue();
            Producto p = productoRepo.findById(idProducto).orElseThrow();

            BigDecimal precioAUsar = BigDecimal.ZERO;
            if ("DIRECTA".equals(d.getTipoCompra()) || "CAJA_MISTERIOSA".equals(d.getTipoCompra())) {
                if (!"DISPONIBLE".equals(p.getEstado())) {
                    throw new RuntimeException("El producto " + p.getNombre() + " ya no está disponible.");
                }
                p.setEstado("RESERVADO");
                productoRepo.save(p);
                precioAUsar = ("DIRECTA".equals(d.getTipoCompra())) ? p.getPrecioBase() : (p.getPrecioTicket() != null ? p.getPrecioTicket() : p.getPrecioBase());
            } else if ("RIFA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioTicket();
            } else if ("SUBASTA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioActual();
            }

            DetalleOrden detalle = new DetalleOrden();
            detalle.setOrden(ordenGuardada);
            detalle.setProducto(p);
            detalle.setCantidad(d.getCantidad());
            detalle.setTipoCompra(d.getTipoCompra());
            detalle.setDatosExtra(d.getDatosExtra());
            detalle.setPrecioUnitario(precioAUsar);

            detalleOrdenRepository.save(detalle);
        }

        return ordenGuardada;
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
        Orden orden = ordenRepository.findByIdConDetalles(java.util.Objects.requireNonNull(id, "ID de orden no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        if (!orden.getUsuario().getEmail().equals(email)) {
            throw new RuntimeException("No tienes permiso para ver esta orden");
        }

        return orden;
    }

    @Transactional
    public void informarPago(Integer id, org.springframework.web.multipart.MultipartFile archivo) throws java.io.IOException {
        Orden orden = ordenRepository.findById(id).orElseThrow();
        
        // Subir comprobante a Azure ☁️
        String url = storageService.subirImagen(archivo);
        
        orden.setComprobanteUrl(url);
        orden.setEstado("ESPERANDO_APROBACION");
        ordenRepository.save(orden);
    }

    @Transactional
    public void aprobarPago(Integer id) {
        pagarOrden(id); // Reutilizamos lógica existente de marcado y correo
    }

    @Transactional
    public void rechazarPago(Integer id, String motivo) {
        Orden orden = ordenRepository.findByIdConDetalles(id)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));
        
        // LIBERAR PRODUCTOS RESERVADOS
        for (DetalleOrden detalle : orden.getDetalles()) {
            Producto p = detalle.getProducto();
            if ("RESERVADO".equals(p.getEstado())) {
                p.setEstado("DISPONIBLE");
                productoRepo.save(p);
            }
        }

        String motivoFinal = (motivo == null || motivo.trim().isEmpty()) 
            ? "El comprobante enviado no es válido o no coincide con el monto de la transferencia." 
            : motivo;

        // Notificación por Email 📧
        try {
            String tiendaNombre = orden.getTienda() != null ? orden.getTienda().getNombre() : "SubastaShop";
            String clienteEmail = orden.getUsuario().getEmail();
            String asunto = "Actualización de tu Orden #" + orden.getId() + " - Pago Rechazado";

            StringBuilder sb = new StringBuilder();
            sb.append("<div style='font-family: Arial, sans-serif; color: #333;'>");
            sb.append("<h2>Hola, ").append(orden.getUsuario().getNombreCompleto()).append("</h2>");
            sb.append("<p>Te informamos que tu pago para la orden <b>#").append(orden.getId()).append("</b> ha sido rechazado por la tienda <b>").append(tiendaNombre).append("</b>.</p>");
            sb.append("<div style='background: #f8d7da; padding: 15px; border-radius: 8px; border-left: 5px solid #dc3545;'>");
            sb.append("<strong>Motivo del rechazo:</strong><br>");
            sb.append("<i>").append(motivoFinal).append("</i>");
            sb.append("</div>");
            sb.append("<p style='margin-top: 20px;'>Debido a este rechazo, <b>la orden ha sido eliminada de nuestro sistema</b> y los productos han vuelto a estar disponibles.</p>");
            sb.append("<p>Te invitamos a realizar una nueva compra o intentar el pago nuevamente más tarde.</p>");
            sb.append("<br><hr style='border: 0; border-top: 1px solid #eee;'>");
            sb.append("<p style='font-size: 0.8em; color: #777;'>Mensaje enviado automáticamente en nombre de <b>").append(tiendaNombre).append("</b> a través de SubastaShop.</p>");
            sb.append("</div>");

            emailService.enviarCorreo(clienteEmail, asunto, sb.toString());
        } catch (Exception e) {
            // Log error
            System.err.println("Error enviando email de rechazo: " + e.getMessage());
        }

        orden.setEstado("CANCELADA"); // Cambiado de PENDIENTE_PAGO a CANCELADA
        ordenRepository.save(orden);
    }

    public List<Orden> obtenerPendientesValidacion(Long tiendaId) {
        return ordenRepository.findByTiendaIdAndEstado(tiendaId, "ESPERANDO_APROBACION");
    }

    @Transactional
    public void cancelarOrden(Long id, String emailUsuario) {
        Orden orden = ordenRepository.findByIdConDetalles(id.intValue())
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));


        // Solo el comprador dueño puede cancelar su propia orden
        if (!orden.getUsuario().getEmail().equals(emailUsuario)) {
            throw new RuntimeException("No tienes permiso para cancelar esta orden.");
        }

        // Solo se pueden cancelar órdenes que aún no han sido pagadas o procesadas
        String estado = orden.getEstado();
        if ("PAGADO".equals(estado) || "CANCELADA".equals(estado)) {
            throw new RuntimeException("Esta orden no puede cancelarse porque ya fue " + estado.toLowerCase() + ".");
        }

        // LIBERAR PRODUCTOS RESERVADOS
        for (DetalleOrden detalle : orden.getDetalles()) {
            Producto p = detalle.getProducto();
            if ("RESERVADO".equals(p.getEstado())) {
                p.setEstado("DISPONIBLE");
                productoRepo.save(p);
            }
        }

        String tiendaNombre = orden.getTienda() != null ? orden.getTienda().getNombre() : "SubastaShop";
        String compradorNombre = orden.getUsuario().getNombreCompleto();
        String compradorEmail = orden.getUsuario().getEmail();

        // --- EMAIL AL COMPRADOR ---
        try {
            String asunto = "Tu Orden #" + orden.getId() + " ha sido cancelada";
            StringBuilder sb = new StringBuilder();
            sb.append("<div style='font-family: Arial, sans-serif; color: #333;'>");
            sb.append("<h2>Hola, ").append(compradorNombre).append("</h2>");
            sb.append("<p>Confirmamos que tu orden <b>#").append(orden.getId()).append("</b> en la tienda <b>").append(tiendaNombre).append("</b> ha sido <b style='color:#dc3545;'>cancelada</b> por ti.</p>");
            sb.append("<div style='background: #f8d7da; padding: 15px; border-radius: 8px; border-left: 5px solid #dc3545; margin: 16px 0;'>");
            sb.append("<b>Productos liberados y disponibles nuevamente.</b> Si deseas, puedes volver a comprarlos cuando quieras.");
            sb.append("</div>");
            sb.append("<p>Si tienes dudas, puedes contactar directamente con la tienda <b>").append(tiendaNombre).append("</b>.</p>");
            sb.append("<br><hr style='border: 0; border-top: 1px solid #eee;'>");
            sb.append("<p style='font-size: 0.8em; color: #777;'>Mensaje enviado automáticamente por SubastaShop.</p>");
            sb.append("</div>");
            emailService.enviarCorreo(compradorEmail, asunto, sb.toString());
        } catch (Exception e) {
            System.err.println("Error enviando email de cancelación al comprador: " + e.getMessage());
        }

        // --- EMAIL AL VENDEDOR ---
        try {
            // Buscar el dueño de la tienda por su tiendaId
            if (orden.getTienda() != null) {
                usuarioRepository.findByTiendaId(orden.getTienda().getId()).ifPresent(vendedor -> {
                    String asuntoVendedor = "El cliente canceló la Orden #" + orden.getId();
                    StringBuilder sbV = new StringBuilder();
                    sbV.append("<div style='font-family: Arial, sans-serif; color: #333;'>");
                    sbV.append("<h2>Cancelación de Orden</h2>");
                    sbV.append("<p>El cliente <b>").append(compradorNombre).append("</b> (").append(compradorEmail).append(") ha cancelado la orden <b>#").append(orden.getId()).append("</b>.</p>");
                    sbV.append("<div style='background: #fff3cd; padding: 15px; border-radius: 8px; border-left: 5px solid #ffc107; margin: 16px 0;'>");
                    sbV.append("<b>Total de la orden cancelada:</b> $").append(orden.getTotal()).append("<br>");
                    sbV.append("<b>Productos:</b> ");
                    orden.getDetalles().forEach(d -> sbV.append(d.getProducto().getNombre()).append(", "));
                    sbV.append("</div>");
                    sbV.append("<p>Los productos han sido liberados y vueltos a estado disponible automáticamente.</p>");
                    sbV.append("<br><hr style='border: 0; border-top: 1px solid #eee;'>");
                    sbV.append("<p style='font-size: 0.8em; color: #777;'>Mensaje enviado automáticamente por SubastaShop.</p></div>");
                    emailService.enviarCorreo(vendedor.getEmail(), asuntoVendedor, sbV.toString());
                });
            }
        } catch (Exception e) {
            System.err.println("Error enviando email de cancelación al vendedor: " + e.getMessage());
        }

        orden.setEstado("CANCELADA");
        ordenRepository.save(orden);
    }

    @Transactional
    public String abrirCajaMisteriosa(Long detalleId, String email) {
        DetalleOrden detalle = detalleOrdenRepository.findById(java.util.Objects.requireNonNull(detalleId, "ID de detalle no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Detalle no encontrado"));

        Orden orden = detalle.getOrden();
        if (!orden.getUsuario().getEmail().equals(email)) {
            throw new RuntimeException("No tienes permiso");
        }
        if (!"PAGADO".equals(orden.getEstado())) {
            throw new RuntimeException("La orden debe estar PAGADA para abrir la caja.");
        }
        
        return revelarPremioCaja(detalle);
    }

    @Transactional
    protected String revelarPremioCaja(DetalleOrden detalle) {
        if (detalle.getDatosExtra() != null && detalle.getDatosExtra().startsWith("Premio:")) {
            return detalle.getDatosExtra().replace("Premio: ", "");
        }

        Producto producto = detalle.getProducto();
        if (!"CAJA_MISTERIOSA".equals(producto.getTipoVenta())) {
            throw new RuntimeException("Este producto no es una Caja Misteriosa.");
        }

        List<com.subastashop.backend.models.PremioCaja> premios = producto.getPremios();
        if (premios == null || premios.isEmpty()) {
            throw new RuntimeException("La caja no tiene premios configurados.");
        }

        // Weighted Random Algorithm
        double totalProb = premios.stream().mapToDouble(p -> p.getProbabilidad() != null ? p.getProbabilidad() : 0.0).sum();
        double randomValue = Math.random() * totalProb;
        double init = 0.0;
        com.subastashop.backend.models.PremioCaja ganador = premios.get(0);

        for (com.subastashop.backend.models.PremioCaja premio : premios) {
            double prob = premio.getProbabilidad() != null ? premio.getProbabilidad() : 0.0;
            init += prob;
            if (randomValue <= init) {
                ganador = premio;
                break;
            }
        }

        // Bajar stock del premio si es relevante
        if (ganador.getStock() != null && ganador.getStock() > 0) {
            ganador.setStock(ganador.getStock() - 1);
        }

        String resultado = "Premio: " + ganador.getNombre();
        detalle.setDatosExtra(resultado);
        detalleOrdenRepository.save(detalle);

        return ganador.getNombre();
    }
}
