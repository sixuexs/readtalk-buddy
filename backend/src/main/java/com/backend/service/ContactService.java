package com.backend.service;

import com.backend.document.ContactDocument;
import com.backend.entity.ContactEntity;
import com.backend.repository.jpa.ContactJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 联系人同步服务 —— MongoDB ContactDocument ↔ MySQL ContactEntity 的桥梁
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactJpaRepository contactJpaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 MongoDB ContactDocument 映射为 MySQL ContactEntity 并持久化。
     *
     * @param doc    MongoDB 中的联系人文档（数据源）
     * @param userId 联系人所属的用户 ID（ContactDocument 不含此字段）
     * @return 已保存的 ContactEntity
     */
    @Transactional
    public ContactEntity saveOrUpdate(ContactDocument doc, Long userId) {
        // 尝试查找已存在的实体
        ContactEntity entity = contactJpaRepository.findByUserIdAndName(userId, doc.getName())
                .orElse(null);

        if (entity != null) {
            // 更新：仅覆盖白名单字段
            entity.setAvatarUrl(doc.getAvatar());
            entity.setRelationType(doc.getRelationType() != null ? doc.getRelationType() : "other");
            entity.setPersonality(doc.getPersonality());
            entity.setInterests(toJson(doc.getInterests()));
            entity.setLabels(toJson(doc.getLabels()));
            entity.setUpdatedAt(LocalDateTime.now());
        } else {
            // 新增：仅设置白名单字段，其他信任 DB 默认
            entity = new ContactEntity();
            entity.setUserId(userId);
            entity.setName(doc.getName());
            entity.setAvatarUrl(doc.getAvatar());
            entity.setRelationType(doc.getRelationType() != null ? doc.getRelationType() : "other");
            entity.setPersonality(doc.getPersonality());
            entity.setInterests(toJson(doc.getInterests()));
            entity.setLabels(toJson(doc.getLabels()));
        }

        // 永不触碰：intimacyScore, lastContactTime, warningDismissedAt, deletedAt, id, createdAt
        return contactJpaRepository.save(entity);
    }

    /**
     * 直接持久化 ContactEntity（用于事件监听器等已持有 Entity 的场景）。
     *
     * @param entity 已构建的 ContactEntity
     * @return 已保存的 ContactEntity
     */
    @Transactional
    public ContactEntity saveOrUpdate(ContactEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        return contactJpaRepository.save(entity);
    }

    /**
     * 按用户 ID 查询联系人列表（排除已软删除的）。
     */
    @Transactional(readOnly = true)
    public List<ContactEntity> findByUserId(Long userId) {
        return contactJpaRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
