package com.backend.repository.jpa;

import com.backend.entity.EvaluationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRecordRepository extends JpaRepository<EvaluationRecord, Long> {

    List<EvaluationRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
