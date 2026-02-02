package com.subastashop.backend.controllers;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ProductoRepository productoRepo; // Arregla 'productoRepo'

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private DetalleOrdenRepository detalleOrdenRepository;

    // SIMULACIÓN DE PAGO
    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> pagarOrden(@PathVariable Integer id) {
        Optional<Orden> ordenOpt = ordenRepository.findById(id);

        if (ordenOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Orden orden = ordenOpt.get();

        if ("PAGADO".equals(orden.getEstado())) {
            return ResponseEntity.badRequest().body("Esta orden ya está pagada.");
        }

        // Aquí iría la lógica real con WebPay / Stripe / PayPal
        // Nosotros simularemos que siempre funciona:

        orden.setEstado("PAGADO");
        ordenRepository.save(orden);

        return ResponseEntity.ok("Pago exitoso. ¡Producto en camino!");
    }

    @PostMapping("/crear")
    @Transactional
    public ResponseEntity<?> crearOrden(@RequestBody OrdenRequest request) {

        // A. OBTENER USUARIO ACTUAL
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuarioActual = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                if (request.getDetalles().isEmpty()) {
            return ResponseEntity.badRequest().body("El carrito no puede estar vacío");
        }

        // Tomamos el primer producto para saber a qué Tienda pertenece esta orden
        // (Asumimos que todos los productos del carrito son de la misma tienda, 
        //  o asignamos la orden a la tienda del primer item).
        Long primerProductoId = request.getDetalles().get(0).getProductoId();
        Producto primerProducto = productoRepo.findById(primerProductoId.intValue())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        
        Tienda tiendaOrden = primerProducto.getTienda();

        // B. CREAR LA ORDEN CABECERA
        Orden orden = new Orden();
        orden.setUsuario(usuarioActual);
        orden.setTienda(tiendaOrden); // ✅ ASIGNAMOS LA TIENDA AQUÍ, ANTES DEL SAVE
        orden.setFechaCreacion(LocalDateTime.now());
        
        // Calculamos una fecha de expiración (ej: 24 horas para pagar)
        orden.setFechaExpiracionReserva(LocalDateTime.now().plusHours(24)); 
        
        orden.setEstado("PENDIENTE_PAGO");
        orden.setTotal(BigDecimal.ZERO);
        
        // AHORA SÍ PODEMOS GUARDAR (Ya tiene usuario y tienda)
        orden = ordenRepository.save(orden); 

        // C. PROCESAR DETALLES (Igual que antes)
        BigDecimal totalOrden = BigDecimal.ZERO; 
        List<DetalleOrden> detallesGuardados = new ArrayList<>();

        // C. PROCESAR CADA ITEM
        for (DetalleRequest d : request.getDetalles()) {
            
            // CORRECCIÓN ERROR 3: Convertimos Long a Integer con .intValue()
            // (Asumiendo que tu ProductoRepository busca por Integer)
            Integer idProducto = d.getProductoId().intValue();

            Producto p = productoRepo.findById(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + idProducto));

            // --- VALIDACIONES Y CÁLCULOS ---
            
            BigDecimal precioAUsar = BigDecimal.ZERO;

            // 1. CASO VENTA DIRECTA
            if ("DIRECTA".equals(d.getTipoCompra())) {
                if (!"DISPONIBLE".equals(p.getEstado())) {
                    throw new RuntimeException("El producto " + p.getNombre() + " ya no está disponible.");
                }
                p.setEstado("RESERVADO");
                productoRepo.save(p);
                
                // Usamos .add() en lugar de +=
                precioAUsar = p.getPrecioBase();
            }

            // 2. CASO RIFA
            else if ("RIFA".equals(d.getTipoCompra())) {
                // Aquí validas stock de rifa...
                precioAUsar = p.getPrecioTicket();
            }

            // 3. CASO SUBASTA
            else if ("SUBASTA".equals(d.getTipoCompra())) {
                precioAUsar = p.getPrecioActual();
            }
            
            // Sumamos al total acumulado (BigDecimal es inmutable, hay que reasignar)
            totalOrden = totalOrden.add(precioAUsar);

            // --- GUARDAR DETALLE ---
            DetalleOrden detalle = new DetalleOrden();
            detalle.setOrden(orden);
            detalle.setProducto(p);
            detalle.setCantidad(d.getCantidad());
            detalle.setTipoCompra(d.getTipoCompra());
            detalle.setDatosExtra(d.getDatosExtra());
            
            // CORRECCIÓN ERROR 7: Pasamos BigDecimal, no double
            detalle.setPrecioUnitario(precioAUsar);

            detalleOrdenRepository.save(detalle);
            detallesGuardados.add(detalle);
        }

        // D. ACTUALIZAR TOTAL ORDEN
        // CORRECCIÓN ERROR 2: Pasamos BigDecimal
        orden.setTotal(totalOrden);
        
        if (!detallesGuardados.isEmpty()) {
            orden.setTienda(detallesGuardados.get(0).getProducto().getTienda());
        }
        ordenRepository.save(orden);

        return ResponseEntity.ok(orden);
    }
    
    // ... el método mis-ordenes ...
    @GetMapping("/mis-ordenes")
    public ResponseEntity<List<Orden>> obtenerMisOrdenes() {
        System.out.println("🚀 ¡Llegó la petición al controlador!");
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email).orElseThrow();
        return ResponseEntity.ok(ordenRepository.findByUsuarioOrderByIdDesc(usuario));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Orden> obtenerOrdenPorId(@PathVariable Integer id) {
        // 1. Obtener usuario actual para seguridad
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // 2. Buscar la orden
        Orden orden = ordenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));

        // 3. SEGURIDAD: Validar que la orden pertenezca al usuario (o sea Admin)
        if (!orden.getUsuario().getEmail().equals(email)) {
             // Aquí podrías lanzar excepción o retornar 403
             return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(orden);
    }
}