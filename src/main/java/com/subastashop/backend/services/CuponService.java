package com.subastashop.backend.services;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.dto.CuponDTO;
import com.subastashop.backend.exceptions.ApiException;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Cupon;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.CuponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CuponService {

    @Autowired
    private CuponRepository cuponRepository;

    @Autowired
    private AppUserRepository usuarioRepository;

    public Cupon crearCupon(String email, CuponDTO dto) {
        AppUsers vendedor = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado"));

        Tienda tienda = vendedor.getTienda();
        if (tienda == null) {
            throw new ApiException("No tienes una tienda configurada para crear cupones.");
        }

        Cupon cupon = new Cupon();
        cupon.setCodigo(dto.getCodigo().toUpperCase());
        cupon.setDescuento(dto.getDescuento());
        cupon.setTipo(dto.getTipo() != null ? dto.getTipo().toUpperCase() : "PORCENTAJE");
        cupon.setFechaExpiracion(dto.getFechaExpiracion());
        cupon.setLimiteUso(dto.getLimiteUso());
        cupon.setActivo(true);
        cupon.setUsosActuales(0);
        cupon.setTienda(tienda);
        cupon.setTenantId(TenantContext.getTenantId());

        return cuponRepository.save(cupon);
    }

    public List<CuponDTO> obtenerMisCupones(String email) {
        AppUsers vendedor = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado"));

        if (vendedor.getTienda() == null) {
            return List.of();
        }

        return cuponRepository.findByTiendaIdAndTenantId(vendedor.getTienda().getId(), TenantContext.getTenantId())
                .stream().map(this::toDTO).toList();
    }

    public CuponDTO validarCupon(String codigo, Integer tiendaId) {
        Cupon cupon = cuponRepository.findByCodigoAndTenantIdAndActivoTrue(codigo.toUpperCase(), TenantContext.getTenantId())
                .orElseThrow(() -> new ApiException("Cupón inválido o inactivo."));

        if (!cupon.getTienda().getId().equals(tiendaId)) {
            throw new ApiException("Este cupón no es válido para esta tienda.");
        }

        if (cupon.getFechaExpiracion() != null && cupon.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ApiException("Este cupón ha expirado.");
        }

        if (cupon.getLimiteUso() != null && cupon.getUsosActuales() >= cupon.getLimiteUso()) {
            throw new ApiException("El cupón ha alcanzado su límite de usos.");
        }

        return toDTO(cupon);
    }

    public void usarCupon(String codigo) {
        Cupon cupon = cuponRepository.findByCodigoAndTenantIdAndActivoTrue(codigo.toUpperCase(), TenantContext.getTenantId())
                .orElseThrow(() -> new ApiException("Cupón inválido"));
        
        cupon.setUsosActuales(cupon.getUsosActuales() + 1);
        if (cupon.getLimiteUso() != null && cupon.getUsosActuales() >= cupon.getLimiteUso()) {
            cupon.setActivo(false);
        }
        cuponRepository.save(cupon);
    }

    public void cambiarEstado(Integer id, String email) {
        AppUsers vendedor = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado"));

        Cupon cupon = cuponRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new ApiException("Cupón no encontrado"));

        if (vendedor.getTienda() == null || !cupon.getTienda().getId().equals(vendedor.getTienda().getId())) {
            throw new ApiException("No tienes permiso para modificar este cupón.");
        }

        cupon.setActivo(!cupon.isActivo());
        cuponRepository.save(cupon);
    }

    private CuponDTO toDTO(Cupon c) {
        CuponDTO dto = new CuponDTO();
        dto.setId(c.getId());
        dto.setCodigo(c.getCodigo());
        dto.setDescuento(c.getDescuento());
        dto.setTipo(c.getTipo());
        dto.setFechaExpiracion(c.getFechaExpiracion());
        dto.setActivo(c.isActivo());
        dto.setLimiteUso(c.getLimiteUso());
        dto.setUsosActuales(c.getUsosActuales());
        dto.setTiendaId(c.getTienda().getId());
        dto.setTiendaNombre(c.getTienda().getNombre());
        return dto;
    }
}
