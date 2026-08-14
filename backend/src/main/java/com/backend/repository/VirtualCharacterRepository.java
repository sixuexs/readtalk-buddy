package com.backend.repository;

import com.backend.document.VirtualCharacterDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VirtualCharacterRepository extends MongoRepository<VirtualCharacterDocument, String> {

    List<VirtualCharacterDocument> findAllByOrderByCreatedAtDesc();
}
