package com.backend.repository;

import com.backend.document.WeeklyReportDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 周报仓储（P3-1）。 */
@Repository
public interface WeeklyReportRepository extends MongoRepository<WeeklyReportDocument, String> {

    List<WeeklyReportDocument> findByUserIdOrderByWeekKeyDesc(Long userId);

    Optional<WeeklyReportDocument> findFirstByUserIdAndWeekKey(Long userId, String weekKey);
}
