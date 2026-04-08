package com.subastashop.backend.repositories;

import com.subastashop.backend.models.ContestWinner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContestWinnerRepository extends JpaRepository<ContestWinner, Integer> {
    boolean existsByContestId(Integer contestId);
    List<ContestWinner> findByContestId(Integer contestId);
}