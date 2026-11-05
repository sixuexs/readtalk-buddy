package com.backend.repository;

import com.backend.document.TrainingPlanDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 训练处方仓储（P2-1）。 */
@Repository
public interface TrainingPlanRepository extends MongoRepository<TrainingPlanDocument, String> {

    Optional<TrainingPlanDocument> findFirstByUserIdAndStatusOrderByGeneratedAtDesc(Long userId, String status);

    Optional<TrainingPlanDocument> findFirstByUserIdAndWeekKeyAndStatus(Long userId, String weekKey, String status);

    List<TrainingPlanDocument> findByUserIdOrderByWeekStartDesc(Long userId);
}
