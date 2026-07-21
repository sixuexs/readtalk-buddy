package com.backend.repository.jpa;

import com.backend.entity.ContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactJpaRepository extends JpaRepository<ContactEntity, Long> {

    List<ContactEntity> findByUserIdAndDeletedAtIsNull(Long userId);
}
