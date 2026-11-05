package com.backend.repository;

import com.backend.document.ConversationDetailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话逐句明细仓储（conversation_detail）。
 *
 * <p>_id = interaction_meta.biz_id（业务幂等键）。
 */
@Repository
public interface ConversationDetailRepository extends MongoRepository<ConversationDetailDocument, String> {

    /** 按用户取明细，按创建时间倒序。 */
    List<ConversationDetailDocument> findByUserIdOrderByCreatedAtDesc(Long userId);
}
