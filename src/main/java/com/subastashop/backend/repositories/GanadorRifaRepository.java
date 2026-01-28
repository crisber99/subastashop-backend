package com.subastashop.backend.repositories;

import com.subastashop.backend.models.GanadorRifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GanadorRifaRepository extends JpaRepository<GanadorRifa, Long> {

    boolean existsByRifaId(Integer rifaId);
}