package com.backend.seed;

import com.backend.document.VirtualCharacterDocument;
import com.backend.repository.VirtualCharacterRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 虚拟人物预置数据。
 *
 * 与情景模拟 4 个性格选项对齐，补全兴趣/标签，供破冰"对方名片"与模拟训练复用。
 * 仅在库为空时插入，避免重复 seed 产生脏数据。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualCharacterSeed {

    private final VirtualCharacterRepository repo;

    @PostConstruct
    public void seed() {
        if (repo.count() > 0) {
            log.info("VirtualCharacterSeed: 已有 {} 个虚拟人物，跳过", repo.count());
            return;
        }

        List<VirtualCharacterDocument> chars = List.of(
                charOf("乐观开朗自来熟", "乐观开朗自来熟",
                        List.of("阅读", "跑步", "旅行"), List.of("话痨", "社牛"), "自来熟的开心果，很快能打开话匣子"),
                charOf("不善交际慢热", "不善交际慢热",
                        List.of("电影", "写作", "音乐"), List.of("宅", "慢热"), "慢热但真诚，熟悉后很健谈"),
                charOf("幽默风趣社牛", "幽默风趣社牛",
                        List.of("脱口秀", "美食", "运动"), List.of("段子手", "社牛"), "段子手，擅长活跃气氛"),
                charOf("沉稳内敛观察者", "沉稳内敛观察者",
                        List.of("摄影", "历史", "茶道"), List.of("观察者", "倾听者"), "话不多但洞察力强，是很好的倾听者")
        );

        repo.saveAll(chars);
        log.info("VirtualCharacterSeed: 已预置 {} 个虚拟人物", chars.size());
    }

    private VirtualCharacterDocument charOf(String name, String personality,
                                            List<String> interests, List<String> labels,
                                            String description) {
        VirtualCharacterDocument doc = new VirtualCharacterDocument();
        doc.setName(name);
        doc.setPersonality(personality);
        doc.setInterests(interests);
        doc.setLabels(labels);
        doc.setDescription(description);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }
}
