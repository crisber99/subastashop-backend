package com.subastashop.backend.repositories;

import com.subastashop.backend.models.UserLegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserLegalAcceptanceRepository extends JpaRepository<UserLegalAcceptance, Long> {
    Optional<UserLegalAcceptance> findFirstByUserIdOrderByAcceptanceTimestampDesc(Integer userId);
}
