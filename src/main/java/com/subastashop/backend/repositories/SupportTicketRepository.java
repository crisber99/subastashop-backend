package com.subastashop.backend.repositories;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUsuarioOrderByFechaCreacionDesc(AppUsers usuario);
    List<SupportTicket> findAllByOrderByFechaCreacionDesc();
}
