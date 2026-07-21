package com.backend.repository.jpa;

import com.backend.entity.InteractionMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InteractionMetaRepository extends JpaRepository<InteractionMeta, Long> {

    Optional<InteractionMeta> findByBizId(String bizId);
}
