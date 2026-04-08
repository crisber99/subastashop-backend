package com.subastashop.backend.repositories;

import com.subastashop.backend.models.MensajeChat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {
    
    // Default naming (will likely fail on query, but app will START)
    List<MensajeChat> findByProductoIdOrderByFechaEnvioAsc(Long productoId);
    
    List<MensajeChat> findTop50ByProductoIdOrderByFechaEnvioAsc(Long productoId);

    List<MensajeChat> findByTiendaIdOrderByFechaEnvioAsc(Long tiendaId);
}
