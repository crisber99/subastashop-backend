package com.subastashop.backend.repositories;

import com.subastashop.backend.models.MensajeChat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {
    
    // Buscar los últimos mensajes para un producto (Conversación individual)
    List<MensajeChat> findByProductoIdOrderByFechaEnvioAsc(Long productoId);
    
    // Con soporte de paginación para limitar historial por producto
    List<MensajeChat> findTop50ByProductoIdOrderByFechaEnvioAsc(Long productoId);

    // Métodos antiguos por tienda (mantener por si acaso para vista admin de tienda)
    List<MensajeChat> findByTiendaIdOrderByFechaEnvioAsc(Long tiendaId);
    List<MensajeChat> findTop50ByTiendaIdOrderByFechaEnvioAsc(Long tiendaId);
}
