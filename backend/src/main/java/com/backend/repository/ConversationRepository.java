package com.backend.repository;

import com.backend.document.ConversationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends MongoRepository<ConversationDocument, String> {

    // 按创建时间倒序获取所有会话
    List<ConversationDocument> findAllByOrderByCreatedAtDesc();

    /**
     * 查某联系人的历史对话（有评分），按创建时间倒序。
     * relatedContactId 为 null 的旧文档天然不被命中。
     */
    List<ConversationDocument> findByRelatedContactIdOrderByCreatedAtDesc(Long relatedContactId);
}
