package com.backend.listener;

import com.backend.event.ContactSavedEvent;
import com.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 书友同步监听器 —— 监听 {@link ContactSavedEvent}，将联系人数据同步到 MySQL。
 *
 * <p>同步流程：
 * <ol>
 *   <li>MongoDB 中 ContactDocument 发生变更</li>
 *   <li>变更方发布 {@link ContactSavedEvent}（携带已构建的 ContactEntity）</li>
 *   <li>本监听器接收事件，调用 {@link ContactService#saveOrUpdate(ContactEntity)} 写入 MySQL</li>
 * </ol>
 *
 * <p>MySQL 不可用时仅记录警告日志，不影响主流程（MongoDB 仍是数据源）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactSyncListener {

    private final ContactService contactService;

    @EventListener(ContactSavedEvent.class)
    public void onContactSaved(ContactSavedEvent event) {
        var contact = event.getContact();
        log.info("收到联系人保存事件: name={}, userId={} — 开始同步到 MySQL",
                contact.getName(), contact.getUserId());

        try {
            contactService.saveOrUpdate(contact);
            log.info("联系人同步到 MySQL 完成: id={}, name={}",
                    contact.getId(), contact.getName());
        } catch (Exception e) {
            log.warn("联系人同步到 MySQL 失败 (MySQL 不可用?): name={}, userId={}, error={}",
                    contact.getName(), contact.getUserId(), e.getMessage());
        }
    }
}
