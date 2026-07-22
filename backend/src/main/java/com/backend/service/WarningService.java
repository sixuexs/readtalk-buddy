package com.backend.service;

import com.backend.constant.WarningConstants;
import com.backend.document.ContactDocument;
import com.backend.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E 模块 C3 — 关系预警规则引擎。
 *
 * 预警源：
 *   1. 亲密度 < DRIFT_INTIMACY_THRESHOLD(40) → 疏远预警
 *   2. lastContactDays > DRIFT_DAYS_THRESHOLD(30) → 超期未联系
 *   3. 质量预警（TODO[P1] — 当前跳过）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarningService {

    private final ContactRepository contactRepo;

    /** 全量扫描，返回提醒 + 预警两个列表。 */
    public Map<String, Object> checkAllContacts() {
        List<Map<String, Object>> reminders = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (var contact : contactRepo.findAll()) {
            // 生日提醒
            if (contact.getBirthday() != null) {
                LocalDate today = LocalDate.now();
                LocalDate next = contact.getBirthday().withYear(today.getYear());
                if (next.isBefore(today)) {
                    next = next.plusYears(1);
                }
                long daysUntil = ChronoUnit.DAYS.between(today, next);
                if (daysUntil <= 7) {
                    reminders.add(Map.of(
                            "contactId", contact.getId(),
                            "name", contact.getName(),
                            "type", "生日提醒",
                            "daysUntil", daysUntil,
                            "suggestion", daysUntil == 0
                                    ? "今天是" + contact.getName() + "的生日，快送上祝福！"
                                    : daysUntil + "天后是" + contact.getName() + "的生日，准备礼物吧"));
                }
            }

            // 长期未联系提醒（14-30天）
            if (contact.getLastContactDays() > WarningConstants.REMINDER_DAYS_THRESHOLD
                    && contact.getLastContactDays() <= WarningConstants.DRIFT_DAYS_THRESHOLD) {
                reminders.add(Map.of(
                        "contactId", contact.getId(),
                        "name", contact.getName(),
                        "type", "联系提醒",
                        "lastContactDays", contact.getLastContactDays(),
                        "suggestion", "已" + contact.getLastContactDays() + "天未联系"
                                + contact.getName() + "，发个消息问候一下吧"));
            }

            // 亲密度预警
            if (contact.getIntimacy() < WarningConstants.DRIFT_INTIMACY_THRESHOLD
                    && !contact.isSuppressWarning() && !contact.isRecovering()) {
                warnings.add(Map.of(
                        "contactId", contact.getId(),
                        "name", contact.getName(),
                        "type", "疏远预警",
                        "intimacy", contact.getIntimacy(),
                        "severity", contact.getIntimacy() < WarningConstants.SEVERE_DRIFT_THRESHOLD
                                ? "严重" : "中等",
                        "suggestion", "与" + contact.getName() + "的关系正在疏远，是否尝试挽救？"));
            }

            // 质量预警：TODO[P1] — P1=无关联字段，当前跳过
        }

        return Map.of("reminders", reminders, "warnings", warnings);
    }

    /** 单联系人疏远检测。 */
    public Map<String, Object> detectDrift(ContactDocument contact) {
        boolean isDrifting = contact.getIntimacy() < WarningConstants.DRIFT_INTIMACY_THRESHOLD
                || contact.getLastContactDays() > WarningConstants.DRIFT_DAYS_THRESHOLD;

        if (isDrifting && !contact.isWarning()) {
            contact.setWarning(true);
            contact.setWarningTime(LocalDateTime.now());
            contactRepo.save(contact);
        }

        return Map.of(
                "contactId", contact.getId(),
                "name", contact.getName(),
                "intimacy", contact.getIntimacy(),
                "lastContactDays", contact.getLastContactDays(),
                "isDrifting", isDrifting,
                "severity", contact.getIntimacy() < WarningConstants.SEVERE_DRIFT_THRESHOLD ? "严重"
                        : contact.getIntimacy() < WarningConstants.DRIFT_INTIMACY_THRESHOLD ? "中等" : "轻微");
    }
}
