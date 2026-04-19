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

        // BUSCAR SI YA EXISTE UNA ORDEN PENDIENTE PARA EL MISMO PRODUCTO (Solo si es 1 detalle, para evitar duplicados en compra directa)
        if (request.getDetalles().size() == 1) {
            Integer productoId = request.getDetalles().get(0).getProductoId().intValue();
            var existete = ordenRepository.findPendingOrderByUserAndProduct(email, productoId);
            if (existete.isPresent()) {
                return existete.get(); // Devolver la existente en lugar de crear otra
            }
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
        orden.setPreferenciaEnvio(request.getPreferenciaEnvio());

        orden = ordenRepository.save(orden);

        BigDecimal totalOrden = BigDecimal.ZERO;
        List<DetalleOrden> detallesGuardados = new ArrayList<>();

        for (DetalleRequest d : request.getDetalles()) {
            Integer idProducto = d.getProductoId().intValue();
            Producto p = productoRepo.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + idProducto));

            BigDecimal precioAUsar = BigDecimal.ZERO;

            if ("DIRECTA".equals(d.getTipoCompra()) || "CAJA_MISTERIOSA".equals(d.getTipoCompra())) {
                if (!"DISPONIBLE".equals(p.getEstado())) {
                    throw new RuntimeException("El producto " + p.getNombre() + " ya no está disponible (estado: " + p.getEstado() + ").");
                }
                p.setEstado("RESERVADO");
                productoRepo.save(p);
                precioAUsar = ("DIRECTA".equals(d.getTipoCompra())) ? p.getPrecioBase() : (p.getPrecioTicket() != null ? p.getPrecioTicket() : p.getPrecioBase());
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
    public void rechazarPago(Integer id) {
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

        orden.setEstado("PENDIENTE_PAGO");
        ordenRepository.save(orden);
    }

    public List<Orden> obtenerPendientesValidacion(Long tiendaId) {
        return ordenRepository.findByTiendaIdAndEstado(tiendaId, "ESPERANDO_APROBACION");
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
