package com.subastashop.backend.repositories;

import com.subastashop.backend.models.PrelaunchSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PrelaunchSubscriberRepository extends JpaRepository<PrelaunchSubscriber, Integer> {
    Optional<PrelaunchSubscriber> findByEmail(String email);
    boolean existsByEmail(String email);
    List<PrelaunchSubscriber> findTop50ByNotifiedFalse();
    List<PrelaunchSubscriber> findTop110ByEmailNotOrderByFechaRegistroAsc(String emailToExclude);
}
