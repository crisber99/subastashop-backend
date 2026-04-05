package com.subastashop.backend.repositories;

import com.subastashop.backend.models.MensajeChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {
    List<MensajeChat> findByProductoIdOrderByFechaEnvioAsc(Integer productoId);
}
