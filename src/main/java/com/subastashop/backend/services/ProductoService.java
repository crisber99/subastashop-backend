package com.subastashop.backend.services;

import com.subastashop.backend.dto.ProductoDTO;
import com.subastashop.backend.exceptions.ApiException;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.CategoriaRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.subastashop.backend.models.PremioCaja;
import com.subastashop.backend.dto.PremioCajaDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private ContentSecurityService securityService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    public Producto crearProducto(String email, boolean isSuperAdmin, List<MultipartFile> archivos, String nombre,
                                  String descripcion, String tipoVenta, BigDecimal precioBase, Integer stock,
                                  String fechaFinIso, BigDecimal precioTicket, Integer cantidadNumeros, Integer cantidadGanadores,
                                  String premiosCajaJson, Integer categoriaId) throws java.io.IOException {

        if (securityService.tieneContenidoIlegal(nombre) || securityService.tieneContenidoIlegal(descripcion)) {
            throw new ApiException("CensoredContent: Tu publicación contiene palabras prohibidas por nuestras normas de comunidad.");
        }

        AppUsers admin = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada para crear productos.");
        }

        Tienda tienda = admin.getTienda();
        if (tienda.getRutEmpresa() == null || tienda.getRutEmpresa().isEmpty() ||
                tienda.getDatosBancarios() == null || tienda.getDatosBancarios().isEmpty()) {
            throw new ApiException("ForbiddenStore: Para publicar, debes configurar RUT y Datos Bancarios en 'Configuración de Tienda'.");
        }

        int limiteImagenes = isSuperAdmin ? 10 : 8;

        if (archivos != null && archivos.size() > limiteImagenes) {
            throw new ApiException("Límite excedido. Tu rol permite un máximo de " + limiteImagenes + " imágenes.");
        }

        List<String> urlsSubidas = new java.util.ArrayList<>();
        if (archivos != null && !archivos.isEmpty()) {
            for (MultipartFile archivo : archivos) {
                if (!archivo.isEmpty()) {
                    String url = azureBlobService.subirImagen(archivo);
                    urlsSubidas.add(url);
                }
            }
        } else {
            urlsSubidas.add("https://placehold.co/600x400?text=Subasta+Shop"); // Imagen por defecto
        }

        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setTipoVenta(tipoVenta);
        p.setPrecioBase(precioBase != null ? precioBase : java.math.BigDecimal.ZERO);
        p.setImagenes(urlsSubidas);
        p.setTienda(admin.getTienda());

        if ("RIFA".equalsIgnoreCase(tipoVenta)) {
            p.setEstado("DISPONIBLE");
            p.setPrecioTicket(precioTicket);
            p.setCantidadNumeros(cantidadNumeros);
            p.setCantidadGanadores(cantidadGanadores);
        } else if ("SUBASTA".equalsIgnoreCase(tipoVenta)) {
            p.setPrecioActual(precioBase);
            p.setEstado("EN_SUBASTA");
            p.setStock(1);

            if (fechaFinIso != null && !fechaFinIso.equals("undefined") && !fechaFinIso.isEmpty()) {
                if (fechaFinIso.length() == 16)
                    fechaFinIso += ":00";
                p.setFechaFinSubasta(LocalDateTime.parse(fechaFinIso));
            }
        } else if ("CAJA_MISTERIOSA".equalsIgnoreCase(tipoVenta)) {
            p.setStock(stock);
            p.setEstado("DISPONIBLE");
            p.setPrecioTicket(precioTicket != null ? precioTicket : precioBase);

            if (premiosCajaJson != null && !premiosCajaJson.isEmpty() && !premiosCajaJson.equals("undefined")) {
                ObjectMapper mapper = new ObjectMapper();
                List<PremioCaja> premios = mapper.readValue(premiosCajaJson, new TypeReference<List<PremioCaja>>() {});
                for (PremioCaja premio : premios) {
                    premio.setProducto(p);
                }
                p.setPremios(premios);
            }
        } else {
            p.setStock(stock);
            p.setEstado("DISPONIBLE");
        }

        if (categoriaId != null) {
            categoriaRepository.findById(categoriaId).ifPresent(p::setCategoria);
        }

        return productoRepository.save(p);
    }

    public Producto editarProducto(Integer id, boolean isSuperAdmin, String nombre, String descripcion, BigDecimal precioBase,
                                   String fechaFin, List<MultipartFile> archivos, Integer categoriaId) throws java.io.IOException {

        if (securityService.tieneContenidoIlegal(nombre) || securityService.tieneContenidoIlegal(descripcion)) {
            throw new ApiException("CensoredContent: No puedes actualizar el producto con términos prohibidos.");
        }
        
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        String estado = producto.getEstado();
        if ("SUBASTA".equals(estado) || "ADJUDICADO".equals(estado) || "PAGADO".equals(estado)) {
            throw new ApiException("No puedes editar un producto activo o vendido.");
        }

        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecioBase(precioBase);

        if (producto.getPujas() == null || producto.getPujas().isEmpty()) {
            producto.setPrecioActual(precioBase);
        }

        if (fechaFin != null && !fechaFin.equals("undefined") && !fechaFin.isEmpty()) {
            if (fechaFin.length() == 16) {
                fechaFin += ":00";
            }
            producto.setFechaFinSubasta(LocalDateTime.parse(fechaFin));
        }

        if (archivos != null && !archivos.isEmpty()) {
            int limiteImagenes = isSuperAdmin ? 10 : 8;

            if (archivos.size() > limiteImagenes) {
                throw new ApiException("Límite excedido. Tu rol permite un máximo de " + limiteImagenes + " imágenes.");
            }

            List<String> urlsSubidas = new java.util.ArrayList<>();
            for (MultipartFile archivo : archivos) {
                if (!archivo.isEmpty()) {
                    String urlNueva = azureBlobService.subirImagen(archivo);
                    urlsSubidas.add(urlNueva);
                }
            }
            producto.setImagenes(urlsSubidas);
        }

        if (categoriaId != null) {
            categoriaRepository.findById(categoriaId).ifPresent(producto::setCategoria);
        }

        return productoRepository.save(producto);
    }

    public ProductoDTO toDTO(Producto p) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId(p.getId());
        dto.setNombre(p.getNombre());
        dto.setSlug(p.getSlug());
        dto.setDescripcion(p.getDescripcion());
        dto.setImagenes(p.getImagenes());
        dto.setTipoVenta(p.getTipoVenta());
        dto.setPrecioBase(p.getPrecioBase());
        dto.setStock(p.getStock());
        dto.setPrecioActual(p.getPrecioActual());
        dto.setFechaFinSubasta(p.getFechaFinSubasta());
        dto.setEstado(p.getEstado());
        dto.setFechaCreacion(p.getFechaCreacion());
        dto.setCantidadNumeros(p.getCantidadNumeros());
        dto.setCantidadGanadores(p.getCantidadGanadores());
        dto.setPrecioTicket(p.getPrecioTicket());
        
        if (p.getTienda() != null) {
            dto.setNombreTienda(p.getTienda().getNombre());
            dto.setSlugTienda(p.getTienda().getSlug());
            // Obtener el ID del dueño (admin) de la tienda
            dto.setTiendaUsuarioId(usuarioRepository.findOwnerIdByTiendaId(p.getTienda().getId()));
        }

        if (p.getCategoria() != null) {
            dto.setCategoriaId(p.getCategoria().getId());
            dto.setCategoriaNombre(p.getCategoria().getNombre());
        }
        
        if (p.getPremios() != null && !p.getPremios().isEmpty()) {
            List<PremioCajaDTO> premiosDTO = new java.util.ArrayList<>();
            for (PremioCaja premio : p.getPremios()) {
                PremioCajaDTO pdto = new PremioCajaDTO();
                pdto.setId(premio.getId());
                pdto.setNombre(premio.getNombre());
                pdto.setImagenUrl(premio.getImagenUrl());
                pdto.setProbabilidad(premio.getProbabilidad());
                pdto.setStock(premio.getStock());
                premiosDTO.add(pdto);
            }
            dto.setPremios(premiosDTO);
        }
        
        return dto;
    }
}
