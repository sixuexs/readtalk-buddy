package com.backend.seed;

import com.backend.document.ContactDocument;
import com.backend.document.ConversationDocument;
import com.backend.document.ConversationDocument.Evaluation;
import com.backend.document.ConversationDocument.MessageItem;
import com.backend.document.UserProfileDocument;
import com.backend.repository.ContactRepository;
import com.backend.repository.ConversationRepository;
import com.backend.repository.UserProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 比赛演示数据 seed（@Profile("demo")，默认不运行）。
 *
 * ⚠️ demo profile 的语义 = 演示库重建：清空 contacts/conversation 全量（含调试遗留与历史真实数据），
 *    只保留纯演示态。日常开发不要用 demo profile 跑。
 *
 * 一键构造"看起来很成熟"的演示态，数据全部 MongoDB：
 *   - 9 个书友：内环/中环/外环分布 + DECAY（严重/中等）+ STAGNATION + 联系提醒 + 生日提醒
 *   - 12 场训练：总分 35→76 上升曲线，真实对话内容，部分关联书友（供深度/质量分量）
 *   - 用户画像：五维均分（由训练聚合）+ 手写的评估/优劣势/提升路线/周目标（含已打卡项）
 *
 * 重置：启动参数 --spring.profiles.active=demo 自动重建；或运行期
 *   curl -X POST http://localhost:8080/api/demo/reset
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DemoShowcaseSeed {

    private final ContactRepository contactRepo;
    private final ConversationRepository conversationRepo;
    private final UserProfileRepository profileRepo;

    /** 书友规格：名字/关系/性格/兴趣/标签/亲密度/未联系天数/生日偏移（null=无生日）/互动次数(近30天) */
    record ContactSpec(String id, String name, String relation, String personality,
                       List<String> interests, List<String> labels,
                       int intimacy, int lastContactDays, Integer birthdayInDays, int recentInteractions) {}

    /** 训练规格：关联书友 id（null=未绑定）/主题/角色性格/几周前/消息条数/五维分/评语/优势/建议 */
    record ConvSpec(String id, String relatedContactId, String theme, String personality,
                    int weeksAgo, int msgCount, int[] dims, String comment,
                    List<String> strengths, List<String> suggestions) {}

    private static final List<ContactSpec> CONTACTS = List.of(
            // 内环 ≥70
            new ContactSpec("demo-c1", "舒雨", "朋友", "开朗健谈，爱分享",
                    List.of("阅读", "科幻", "摄影"), List.of("书虫", "科幻迷"), 82, 1, 5, 4),
            new ContactSpec("demo-c2", "林浩", "同学", "阳光直爽",
                    List.of("篮球", "旅行", "美食"), List.of("运动达人"), 74, 3, null, 3),
            new ContactSpec("demo-c9", "可依", "同学", "细心靠谱",
                    List.of("心理学", "手账", "咖啡"), List.of("学霸"), 70, 6, null, 2),
            // 中环 40-69
            new ContactSpec("demo-c3", "云策", "同事", "沉稳内敛",
                    List.of("电影", "推理", "历史"), List.of("影迷", "细节控"), 55, 8, null, 2),
            new ContactSpec("demo-c4", "梅杰", "同学", "温和慢热",
                    List.of("音乐", "吉他", "民谣"), List.of("文艺青年"), 48, 12, null, 1),
            new ContactSpec("demo-c5", "老周", "朋友", "老练健谈",
                    List.of("历史", "茶道", "书法"), List.of("国学爱好者"), 42, 20, null, 1),
            // 外环 <40：预警区
            new ContactSpec("demo-c6", "魏坚", "同事", "理性严肃",
                    List.of("编程", "技术", "数码"), List.of("极客"), 28, 45, null, 0),
            new ContactSpec("demo-c7", "阿强", "朋友", "大大咧咧",
                    List.of("钓鱼", "户外", "露营"), List.of("钓友"), 15, 62, null, 0),
            new ContactSpec("demo-c8", "柯柯", "朋友", "敏感细腻",
                    List.of("绘画", "展览", "设计"), List.of("设计师"), 44, 38, null, 0)
    );

    private static final List<ConvSpec> CONVERSATIONS = List.of(
            new ConvSpec("demo-s1", null,     "初次见面",   "不善交际慢热",   10, 4,
                    new int[]{35, 40, 30, 30, 40},
                    "开场紧张明显，短句为主，几乎不主动展开话题。建议从共同场景切入，先练把一句话说完整。",
                    List.of("态度真诚"), List.of("延长表达", "主动提问")),
            new ConvSpec("demo-s2", null,     "日常闲聊",   "幽默风趣社牛",    9, 6,
                    new int[]{42, 45, 38, 50, 45},
                    "能接住对方话头但多是附和，缺少自我表露。试着分享一个具体细节，对话会更立体。",
                    List.of("接话自然"), List.of("自我表露", "追问延伸")),
            new ConvSpec("demo-s3", "demo-c7", "初次见面",   "乐观开朗自来熟",  8, 6,
                    new int[]{50, 48, 45, 55, 52},
                    "状态比上次放松，敢主动介绍自己了。共情回应开始出现，继续保持。",
                    List.of("主动介绍", "情绪呼应"), List.of("话题推进")),
            new ConvSpec("demo-s4", null,     "面试演练",   "沉稳内敛观察者",  7, 8,
                    new int[]{55, 60, 45, 50, 48},
                    "回答有条理（先结论后例子），但被打断后容易慌。练习平和地重申观点，不急着道歉。",
                    List.of("结构清晰"), List.of("抗压表达", "共情回应")),
            new ConvSpec("demo-s5", "demo-c6", "读书交流",   "不善交际慢热",    6, 8,
                    new int[]{58, 62, 55, 58, 55},
                    "讨论《三体》时能复述对方观点再展开，这是好的倾听信号。开场白还可以更简洁。",
                    List.of("复述观点", "论据支撑"), List.of("精简开场")),
            new ConvSpec("demo-s6", null,     "读后感分享", "幽默风趣社牛",    5, 10,
                    new int[]{62, 65, 60, 62, 60},
                    "分享时有画面感，能照顾听者反应。逻辑链偶尔跳跃，注意观点间的过渡句。",
                    List.of("表达生动", "照顾听者"), List.of("逻辑过渡")),
            new ConvSpec("demo-s7", "demo-c1", "读书交流",   "乐观开朗自来熟",  4, 10,
                    new int[]{65, 68, 63, 68, 62},
                    "和熟悉的书友主题聊得更开，提问质量提升明显。松弛感还差一口气——允许自己停顿。",
                    List.of("主动提问", "话题延展"), List.of("情绪松弛")),
            new ConvSpec("demo-s8", null,     "日常闲聊",   "沉稳内敛观察者",  3, 12,
                    new int[]{68, 70, 66, 72, 68},
                    "会主动cue对方沉默的点了，互动平衡感不错。表达冗余继续减少。",
                    List.of("邀请让渡", "推进话题"), List.of("精简表达")),
            new ConvSpec("demo-s9", "demo-c3", "读后感分享", "不善交际慢热",    2, 12,
                    new int[]{70, 72, 70, 72, 70},
                    "和慢热型角色对话也能维持节奏了，共情回应具体不空泛。综合稳定站上70。",
                    List.of("共情具体", "节奏稳定"), List.of("主动开场")),
            new ConvSpec("demo-s10", "demo-c1", "初次见面",  "幽默风趣社牛",    1, 12,
                    new int[]{74, 74, 72, 76, 72},
                    "开场自然幽默，能接梗也能收。被质疑时不防御、能重申立场，是明显进步。",
                    List.of("幽默收放", "立场稳定"), List.of("深度延展")),
            new ConvSpec("demo-s11", null,    "面试演练",   "沉稳内敛观察者",  0, 14,
                    new int[]{76, 80, 74, 76, 75},
                    "结构化表达成熟（背景-行动-结果），追问时能命名对方关切。再练高压反驳场景。",
                    List.of("结构成熟", "识别关切"), List.of("高压应对")),
            new ConvSpec("demo-s12", "demo-c2", "日常闲聊",  "乐观开朗自来熟",  0, 14,
                    new int[]{80, 78, 78, 82, 80},
                    "当前最好的综合表现：主动推进、平衡发言、情绪从容。保持节奏，向真实社交迁移。",
                    List.of("推进自然", "情绪从容", "平衡发言"), List.of("迁移真实场景"))
    );

    /** 5 套主题对话剧本（供各训练会话按 msgCount 截取复用） */
    private static final String[][] SCRIPTS = {
            // 初次见面
            {"你好，你是哪个部门的呀？之前好像没见过你。", "你好呀，我是新来的产品经理，负责读书会这块。",
                    "难怪！我是舒雨，科幻组的。你平时也看科幻吗？", "看的！最近刚补完《三体》，被硬科幻震撼到了。",
                    "太巧了，我们组下周正好有三体分享会，你要不要来？", "好啊，正好想找人讨论黑暗森林法则。"},
            // 读书交流
            {"这次分享会你准备了哪本书？", "带了《基地》，阿西莫夫的，想聊聊心理史观。",
                    "这本我读过！你更想聊设定还是人物？", "想先聊设定，心理史观和预测未来那段特别有意思。",
                    "我觉得它的核心其实是对群体行为的隐喻。", "对，个人在历史洪流里很渺小，这个角度我们可以碰一碰。"},
            // 读后感分享
            {"你读完这本最大的感受是什么？", "一句话：命运感。人物的每次选择都像早已写好。",
                    "嗯…具体是哪一段让你有这个感觉？", "第四章他回头望火车站那段，我停了很久。",
                    "被你这么一说我也想起那个镜头了。", "下次分享会我打算就带着这段文本去讲。"},
            // 面试演练
            {"先简单做个自我介绍吧。", "好的。我有两年社区运营经验，组织过40多场线下读书会。",
                    "遇到过参与者冷场吗？怎么处理？", "遇到过。我会先抛一个二选一的低门槛问题，把发言成本降下来。",
                    "如果预算被砍一半呢？", "先和团队对齐核心目标，砍掉装饰性环节，保证主体验不受影响。"},
            // 日常闲聊
            {"周末去哪玩了？看你朋友圈发了照片。", "去了趟美术馆，人不多，逛着很放松。",
                    "我也一直想去！哪个展？", "莫奈特展， prints 很值得看，光线做得特别妙。",
                    "那下周末约一个？我请咖啡。", "好啊，正好跟你聊聊我最近的摄影。"},
    };

    @PostConstruct
    public void rebuild() {
        log.info("========== DemoShowcaseSeed: REBUILD ==========");

        // ── 全量清空，保证纯演示态（demo 库语义；日常开发勿用此 profile） ──
        int oldContacts = (int) contactRepo.count();
        int oldConvs = (int) conversationRepo.count();
        contactRepo.deleteAll();
        conversationRepo.deleteAll();
        log.info("清空演示库旧数据: contacts={} conversations={}", oldContacts, oldConvs);

        LocalDateTime now = LocalDateTime.now();

        // ── 书友 ──
        for (ContactSpec spec : CONTACTS) {
            contactRepo.save(buildContact(spec, now));
        }
        log.info("已创建 {} 个演示书友", CONTACTS.size());

        // ── 训练会话（createdAt 均匀铺开，保证图谱/时间线自然） ──
        for (int i = 0; i < CONVERSATIONS.size(); i++) {
            ConvSpec spec = CONVERSATIONS.get(i);
            conversationRepo.save(buildConversation(spec, now, i));
        }
        log.info("已创建 {} 场演示训练", CONVERSATIONS.size());

        // ── 用户画像（手写评估文案 + 五维均分聚合自演示训练） ──
        profileRepo.save(buildProfile(now));
        log.info("已重建演示画像");

        log.info("========== DemoShowcaseSeed: DONE ==========");
        log.info("演示态: 图谱内环3/中环3/外环3 | DECAY预警2(阿强15分严重/魏坚28分中等) | STAGNATION1(柯柯38天) | 生日提醒(舒雨5天后) | 联系提醒(老周20天) | 成长曲线 35→76");
    }

    // ── builders ──

    private ContactDocument buildContact(ContactSpec s, LocalDateTime now) {
        ContactDocument doc = new ContactDocument();
        doc.setId(s.id());
        doc.setName(s.name());
        doc.setRelationType(s.relation());
        doc.setPersonality(s.personality());
        doc.setInterests(new ArrayList<>(s.interests()));
        doc.setLabels(new ArrayList<>(s.labels()));
        doc.setIntimacy(s.intimacy());
        doc.setLastContactDays(s.lastContactDays());
        doc.setBirthday(s.birthdayInDays() == null ? null : LocalDate.now().plusDays(s.birthdayInDays()));
        doc.setCreatedAt(now.minusDays((long) CONTACTS.indexOf(s) * 7L + 60));
        doc.setUpdatedAt(now.minusDays(s.lastContactDays()));

        // 近30天互动记录（频率分量数据源）
        List<ContactDocument.InteractionRecord> interactions = new ArrayList<>();
        for (int i = 0; i < s.recentInteractions(); i++) {
            ContactDocument.InteractionRecord rec = new ContactDocument.InteractionRecord();
            rec.setType(i % 2 == 0 ? "聊天" : "见面");
            rec.setSummary(s.name() + " 交流近况");
            rec.setTime(now.minusDays(2L + (long) i * 6));
            interactions.add(rec);
        }
        doc.setInteractions(interactions);
        return doc;
    }

    private ConversationDocument buildConversation(ConvSpec s, LocalDateTime now, int index) {
        LocalDateTime createdAt = now.minusWeeks(s.weeksAgo()).minusDays(index % 5);

        // 按主题选剧本，消息条数取前 msgCount 轮（超出则循环，角色严格交替）
        int scriptIdx = scriptFor(s.theme());
        String[] script = SCRIPTS[scriptIdx];

        List<MessageItem> messages = new ArrayList<>();
        for (int i = 0; i < s.msgCount(); i++) {
            MessageItem msg = new MessageItem();
            msg.setMessageOrder(i + 1);
            msg.setRole(i % 2 == 0 ? "other" : "self");
            msg.setAvatar("");
            msg.setContent(script[i % script.length]);
            msg.setTimestamp(createdAt.plusMinutes((long) i * 2)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            messages.add(msg);
        }

        Evaluation eval = new Evaluation();
        eval.setClarity(s.dims()[0]);
        eval.setLogicality(s.dims()[1]);
        eval.setEmpathyListening(s.dims()[2]);
        eval.setInteractivity(s.dims()[3]);
        eval.setRelaxation(s.dims()[4]);
        eval.setComment(s.comment());
        eval.setStrengths(new ArrayList<>(s.strengths()));
        eval.setSuggestions(new ArrayList<>(s.suggestions()));

        ConversationDocument doc = new ConversationDocument();
        doc.setId(s.id());
        doc.setTheme(s.theme());
        doc.setPersonality(s.personality());
        doc.setRelatedContactId(s.relatedContactId());
        doc.setSystemPrompt("演示数据");
        doc.setScore((int) Math.round(
                (s.dims()[0] + s.dims()[1] + s.dims()[2] + s.dims()[3] + s.dims()[4]) / 5.0));
        doc.setEvaluation(eval);
        doc.setMessages(messages);
        doc.setCreatedAt(createdAt);
        doc.setUpdatedAt(createdAt);
        return doc;
    }

    private int scriptFor(String theme) {
        return switch (theme) {
            case "初次见面" -> 0;
            case "读书交流" -> 1;
            case "读后感分享" -> 2;
            case "面试演练" -> 3;
            default -> 4; // 日常闲聊
        };
    }

    private UserProfileDocument buildProfile(LocalDateTime now) {
        UserProfileDocument doc = new UserProfileDocument();
        doc.setId("default");
        doc.setUserId(1L);

        // 五维均分 = 全部演示训练该维平均
        double avgC = 0, avgL = 0, avgE = 0, avgI = 0, avgR = 0;
        List<UserProfileDocument.ScoreRecord> history = new ArrayList<>();
        for (ConvSpec s : CONVERSATIONS) {
            avgC += s.dims()[0]; avgL += s.dims()[1]; avgE += s.dims()[2];
            avgI += s.dims()[3]; avgR += s.dims()[4];

            LocalDateTime scoredAt = now.minusWeeks(s.weeksAgo()).minusDays(CONVERSATIONS.indexOf(s) % 5);
            history.add(new UserProfileDocument.ScoreRecord(
                    s.id(), s.theme(),
                    (int) Math.round((s.dims()[0] + s.dims()[1] + s.dims()[2] + s.dims()[3] + s.dims()[4]) / 5.0),
                    s.dims()[0], s.dims()[1], s.dims()[2], s.dims()[3], s.dims()[4], scoredAt));
        }
        int n = CONVERSATIONS.size();
        doc.setAvgClarity((int) Math.round(avgC / n));
        doc.setAvgLogicality((int) Math.round(avgL / n));
        doc.setAvgEmpathyListening((int) Math.round(avgE / n));
        doc.setAvgInteractivity((int) Math.round(avgI / n));
        doc.setAvgRelaxation((int) Math.round(avgR / n));
        doc.setOverallScore((int) Math.round((avgC + avgL + avgE + avgI + avgR) / n / 5.0));
        doc.setTotalSessions(n);
        doc.setScoreHistory(history);

        doc.setAssessment("两个月训练进步显著：综合分从 37 提升到 80。已从「不敢开口」进阶到「能主导话题」，结构化表达与共情回应基本成型。"
                + "当前瓶颈在高压场景（被质疑、冷场）下的情绪稳定性，建议将训练成果迁移到真实社交验证。");
        doc.setTopStrengths(new ArrayList<>(List.of("结构清晰", "共情具体", "话题推进")));
        doc.setTopWeaknesses(new ArrayList<>(List.of("紧张时道歉退让", "开场冗余")));
        doc.setImprovementPlan("三阶段路线：① 本周在低压力真实场景（和熟悉书友闲聊）完成 2 次真实交流并复盘；"
                + "② 下周挑战中压力场景（读书会发言、新面孔初识），重点练「被质疑时不道歉重申立场」；"
                + "③ 第三周复盘迁移情况，若松弛感均分站稳 75+，进入「主动组织小型分享会」的高阶训练。");
        doc.setWeeklyGoals(new ArrayList<>(List.of(
                "完成 1 次线下读书会主动发言（≥2 分钟）",
                "与舒雨进行一次 15 分钟以上的当面交流",
                "模拟训练中使用「不道歉重申立场」话术 ≥3 次")));
        doc.setWeeklyGoalsStatus(new ArrayList<>(List.of(false, true, false)));

        // 名片字段（演示人设）
        doc.setDisplayName("小阅");
        doc.setAvatar("🦊");
        doc.setStatus("正在读《三体》");
        doc.setBiography("理工科出身、被迫点满社交技能树的阅读爱好者。");
        doc.setPersonality("慢热、真诚、正在变外向");
        doc.setInterests(new ArrayList<>(List.of("阅读", "科幻", "跑步", "摄影")));
        doc.setLabels(new ArrayList<>(List.of("读书会员", "科幻迷", "新手铲屎官")));

        doc.setLastUpdated(now.minusHours(2));
        return doc;
    }
}
