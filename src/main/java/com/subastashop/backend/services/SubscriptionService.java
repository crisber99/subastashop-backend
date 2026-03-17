package com.subastashop.backend.services;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SubscriptionService {

    private final AppUserRepository userRepository;
    private final ProductoRepository productoRepository;

    public SubscriptionService(AppUserRepository userRepository, ProductoRepository productoRepository) {
        this.userRepository = userRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public void processExpiredTrials() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Iniciando revisión de trials vencidos a las {}", now);

        // Buscamos usuarios que sean ADMIN, tengan trial vencido y no tengan suscripción activa
        List<AppUsers> usersToDowngrade = userRepository.findByRolAndFechaFinPruebaBeforeAndSuscripcionActivaFalse(
                Role.ROLE_ADMIN, now
        );

        for (AppUsers user : usersToDowngrade) {
            log.warn("Periodo de prueba vencido para el usuario: {}. Aplicando downgrade.", user.getEmail());

            // 1. Cambiar Rol de ADMIN a COMPRADOR (lo que le quita el acceso al panel)
            user.setRol(Role.ROLE_COMPRADOR);
            userRepository.save(user);

            // 2. Pausar productos si tiene una tienda asociada
            if (user.getTienda() != null) {
                Long tiendaId = user.getTienda().getId();
                List<Producto> productos = productoRepository.findByTiendaId(tiendaId);
                for (Producto p : productos) {
                    if ("DISPONIBLE".equals(p.getEstado()) || "EN_SUBASTA".equals(p.getEstado())) {
                        p.setEstado("PAUSADO");
                        productoRepository.save(p);
                    }
                }
                log.info("Productos de la tienda {} (ID: {}) han sido pausados.", user.getTienda().getNombre(), tiendaId);
            }
        }

        log.info("Finalizada la revisión de trials. Usuarios procesados: {}", usersToDowngrade.size());
    }
}
