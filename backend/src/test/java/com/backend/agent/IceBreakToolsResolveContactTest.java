package com.backend.agent;

import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 破冰建档去重验证（不触发 LLM，直接测 resolveContact）。
 *
 * 覆盖场景：
 *   A. 传已有 contactId → 按 id 复用
 *   B. 不传 id、名字已存在（虚拟人物第二次选中/重复扫码）→ 按名字去重复用
 *   C. 全新名字 → 返回 null，走新建
 *   D. 传了不存在的 id → 回退名字去重
 *
 * 用固定 id 的临时联系人，测试结束清理，不污染业务数据。
 */
@SpringBootTest
class IceBreakToolsResolveContactTest {

    @Autowired
    private IceBreakTools iceBreakTools;

    @Autowired
    private ContactRepository contactRepo;

    private static final String FIXTURE_ID = "test-icebreak-fixture";
    private static final String FIXTURE_NAME = "破冰去重测试人物";

    private ContactDocument fixture() {
        ContactDocument doc = new ContactDocument();
        doc.setId(FIXTURE_ID);
        doc.setName(FIXTURE_NAME);
        doc.setRelationType("朋友");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    @Test
    void resolvesById_whenContactIdGiven() {
        contactRepo.save(fixture());
        try {
            ContactDocument found = iceBreakTools.resolveContact(FIXTURE_ID, List.of("任意标签"));
            assertNotNull(found);
            assertEquals(FIXTURE_ID, found.getId());
        } finally {
            contactRepo.deleteById(FIXTURE_ID);
        }
    }

    @Test
    void resolvesByName_whenNoIdAndNameExists() {
        contactRepo.save(fixture());
        try {
            ContactDocument found = iceBreakTools.resolveContact(null, List.of(FIXTURE_NAME, "其他标签"));
            assertNotNull(found, "同名联系人应被复用，而非重复建档");
            assertEquals(FIXTURE_ID, found.getId());
        } finally {
            contactRepo.deleteById(FIXTURE_ID);
        }
    }

    @Test
    void returnsNull_forBrandNewName() {
        ContactDocument found = iceBreakTools.resolveContact(null, List.of("绝不可能存在的破冰人物XYZ"));
        assertNull(found, "新名字应返回 null 走新建分支");
    }

    @Test
    void fallsBackToName_whenIdStale() {
        contactRepo.save(fixture());
        try {
            ContactDocument found = iceBreakTools.resolveContact("nonexistent-id-123", List.of(FIXTURE_NAME));
            assertNotNull(found);
            assertEquals(FIXTURE_ID, found.getId(), "id 失效时应回退按名字命中");
        } finally {
            contactRepo.deleteById(FIXTURE_ID);
        }
    }
}
