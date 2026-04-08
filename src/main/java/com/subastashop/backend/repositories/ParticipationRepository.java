package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Participation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParticipationRepository extends JpaRepository<Participation, Long> {
    List<Participation> findByContestId(Integer contestId);
    List<Participation> findByContestIdAndParticipantId(Integer contestId, Integer participantId);
    List<Participation> findByParticipantId(Integer participantId);
    
    // Method for contest winners calculation
    List<Participation> findByContestIdAndPaidTrueOrderByDurationMsAscCreatedAtAsc(Integer contestId);
}