package com.subastashop.backend.repositories;
import com.subastashop.backend.models.Orden;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Integer> {

    List<Orden> findByUsuarioId(Integer usuarioId);
}