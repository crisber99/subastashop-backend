package com.subastashop.backend.repositories;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Orden;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Integer> {

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Orden o LEFT JOIN FETCH o.detalles WHERE o.id = :id")
    java.util.Optional<Orden> findByIdConDetalles(Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Orden o LEFT JOIN FETCH o.detalles WHERE o.usuario = :usuario ORDER BY o.id DESC")
    List<Orden> findByUsuarioOrderByIdDesc(AppUsers usuario);
}