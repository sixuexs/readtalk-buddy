package com.backend.repository;

import com.backend.document.UserProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfileDocument, String> {

    /** 获取用户画像（单用户模式，id="default"） */
    default Optional<UserProfileDocument> findDefault() {
        return findById("default");
    }
}
