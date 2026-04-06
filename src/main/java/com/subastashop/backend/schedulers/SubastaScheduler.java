package com.subastashop.backend.schedulers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.DetalleOrden;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.OrdenRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SubastaScheduler {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PujaRepository pujaRepository;

    @Autowired
    private OrdenRepository ordenRepository; // <--- Necesitaremos crear este repo

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private com.subastashop.backend.services.SubastaSniperService sniperService;

    @Autowired
    private com.subastashop.backend.services.EmailService emailService;

    // Se ejecuta cada 60.000 ms (1 minuto) para cierres definitivos
    @Scheduled(fixedRate = 60000)
    public void cerrarSubastasVencidas() {
        System.out.println("⏰ Revisando subastas vencidas..." + LocalDateTime.now());

        List<Producto> productosVencidos = productoRepository.buscarSubastasPorCerrar(LocalDateTime.now());

        for (Producto p : productosVencidos) {
            procesarCierre(p);
        }
    }

    // Proceso de Sniper: Se ejecuta cada 20 segundos para subastas que terminan pronto
    @Scheduled(fixedRate = 20000)
    public void ejecutarSnipersUltimoMinuto() {
        // Buscamos productos que terminan en menos de 1 minuto y que siguen activos
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime unMinutoDespues = ahora.plusMinutes(1);
        
        List<Producto> terminandoPronto = productoRepository.findByEstadoAndFechaFinSubastaBetween(
            "EN_SUBASTA", ahora, unMinutoDespues);
            
        for (Producto p : terminandoPronto) {
            // Seteamos el tenant para que sniperService funcione en el contexto correcto
            TenantContext.setTenantId(p.getTenantId());
            try {
                // El excluyente es nulo porque queremos que el bot puje si no es el ganador actual
                sniperService.procesarSnipers(p.getId(), null); 
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    public void procesarCierre(Producto p) {
        try {
            // CRÍTICO: Seteamos el contexto del Tenant para que las relaciones funcionen
            TenantContext.setTenantId(p.getTenantId());

            System.out.println("Cerrando subasta ID: " + p.getId() + " Tienda: " + p.getTenantId());

            // 1. Buscar la puja más alta
            List<Puja> pujas = pujaRepository.findByProductoIdOrderByMontoDesc(p.getId());

            if (pujas.isEmpty()) {
                p.setEstado("DESIERTO"); // Nadie ofertó
            } else {
                Puja ganadora = pujas.get(0);

                // 2. Adjudicar al ganador
                p.setEstado("ADJUDICADO"); // Ya no se puede ofertar

                // 3. Crear la Orden (Carrito) automáticamente
                Orden orden = new Orden();
                AppUsers usuarioGanador = appUserRepository.findById(ganadora.getUsuarioId())
                        .orElseThrow(() -> new RuntimeException("Usuario ganador no encontrado"));
                orden.setUsuario(usuarioGanador);
                orden.setTienda(p.getTienda());
                orden.setTotal(ganadora.getMonto());
                orden.setEstado("PENDIENTE_PAGO");
                orden.setFechaExpiracionReserva(LocalDateTime.now().plusHours(3));

                // CREAR EL DETALLE (Aquí va el producto ahora)
                DetalleOrden detalle = DetalleOrden.builder()
                        .producto(p)
                        .cantidad(1)
                        .precioUnitario(ganadora.getMonto())
                        .tipoCompra("SUBASTA_GANADA")
                        .datosExtra("Ganador Subasta #" + p.getId())
                        .build();

                // Agregamos el detalle a la orden (usando el método helper que creamos)
                orden.addDetalle(detalle);

                // 4. GUARDAR
                ordenRepository.save(orden);
                System.out.println("--> Orden creada para usuario " + ganadora.getUsuarioId());

                // 5. ENVIAR CORREO AL GANADOR
                try {
                    String destino = usuarioGanador.getEmail();
                    String asunto = "¡Ganaste la subasta de " + p.getNombre() + "!";
                    String mensaje = "Hola " + usuarioGanador.getNombreCompleto() + ",<br><br>" +
                            "¡Excelentes noticias! Tu puja de <b>$" + ganadora.getMonto()
                            + "</b> fue la más alta y has ganado la subasta de '" + p.getNombre() + "'.<br><br>" +
                            "Hemos generado automáticamente una orden de compra en tu cuenta con un plazo de reserva de 3 horas. Por favor, ingresa a la plataforma y completa el pago lo antes posible para asegurar tu producto.<br><br>"
                            +
                            "¡Felicidades y gracias por participar en SubastaShop!<br><br>" +
                            "Saludos,<br>El equipo de SubastaShop";
                    emailService.enviarCorreo(destino, asunto, mensaje);
                } catch (Exception e) {
                    System.err
                            .println("Error enviando correo a ganador de subasta " + p.getId() + ": " + e.getMessage());
                }
            }

            // Guardar cambios del producto
            productoRepository.save(p);

        } catch (Exception e) {
            System.err.println("Error cerrando subasta " + p.getId() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Limpiar contexto al terminar este producto
            TenantContext.clear();
        }
    }
}