package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Participation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
    long countByContestId(Integer contestId);
    List<Participation> findByContestId(Integer contestId);
    List<Participation> findByContestIdAndParticipantId(Integer contestId, Integer participantId);
    
    // Nueva lógica de ganadores: Ordenar por tiempo y fecha
    List<Participation> findByContestIdAndPaidTrueOrderByDurationMsAscCreatedAtAsc(Integer contestId);
}