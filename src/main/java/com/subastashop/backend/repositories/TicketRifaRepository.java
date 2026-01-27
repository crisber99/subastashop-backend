package com.subastashop.backend.repositories;

import com.subastashop.backend.models.TicketRifa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRifaRepository extends JpaRepository<TicketRifa, Long> {
    long countByRifaId(Integer rifaId);
    List<TicketRifa> findByRifaId(Integer rifaId);
    boolean existsByRifaIdAndNumeroTicket(Integer rifaId, Integer numeroTicket);
}