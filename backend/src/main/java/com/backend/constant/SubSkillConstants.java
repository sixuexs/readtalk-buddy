package com.backend.constant;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 子技能词典 —— 能力提升训练闭环（P1）的唯一参数源，仿 IntimacyConstants 纪律。
 *
 * <p>内容 = 设计文档《能力提升模块重构设计》§2.1 词典表 + §4.2 推荐映射表 的逐行转写。
 * 短名 snake_case 冻结（C 文档 §0.2 纪律）：DB / AI prompt / 前端三方共用，禁止起别名。
 *
 * <p>维度短名沿用 C 文档：clarity / logicality / empathy_listening / interactivity / relaxation。
 */
public final class SubSkillConstants {

    /** 子技能词典项。 */
    public record SubSkill(String key, String cnName, String dimension, String definition,
                           String signal, String criteria,
                           String theme, String personality, String feed) {}

    /** 今日推荐训练（设计文档 §4.2 映射行的视图）。 */
    public record Recommendation(String subSkill, String cnName, String theme, String personality, String feed) {}

    // ──── 统一阈值/窗口常量（封测校准只改这里） ────
    /** 子技能可进入诊断/处方的最小含分场次（样本约束）。 */
    public static final int MIN_SAMPLES = 2;
    /** 均值聚合窗口：最近 N 场含分子技能分的会话。 */
    public static final int RECENT_SESSIONS = 10;
    /** auto 验证任务的子技能达标分。 */
    public static final int AUTO_MIN_SCORE = 70;
    /** 掌握徽章阈值。 */
    public static final int MASTERY_L1 = 80;
    public static final int MASTERY_L2 = 90;
    /** 每场评分的证据条数上限 / quote 截断长度。 */
    public static final int EVIDENCE_MAX = 4;
    public static final int QUOTE_MAX_LEN = 60;

    // ──── 五维短名（C 文档 §0.2） ────
    public static final String DIM_CLARITY = "clarity";
    public static final String DIM_LOGICALITY = "logicality";
    public static final String DIM_EMPATHY_LISTENING = "empathy_listening";
    public static final String DIM_INTERACTIVITY = "interactivity";
    public static final String DIM_RELAXATION = "relaxation";

    private static final Map<String, String> DIM_CN = Map.of(
            DIM_CLARITY, "表达清晰度",
            DIM_LOGICALITY, "逻辑思辨力",
            DIM_EMPATHY_LISTENING, "共情与倾听",
            DIM_INTERACTIVITY, "互动积极性",
            DIM_RELAXATION, "情绪松弛度");

    /** 16 项子技能（顺序即展示顺序；与设计文档 §2.1 表逐行一致）。 */
    public static final List<SubSkill> ALL = List.of(
            new SubSkill("filler_control", "填充词控制", DIM_CLARITY,
                    "减少\"那个/就是说/嗯/呃\"等停顿填充",
                    "填充词密度（每百字，<3不扣，口径同B文档）",
                    "90+几乎无填充；70-89偶发不影响；40-69明显口头禅；<40密集",
                    "日常闲聊", "幽默风趣社牛",
                    "你语速快、频繁抛梗和追问，制造节奏压力，让对方需要快速组织语言"),
            new SubSkill("point_first", "观点先行", DIM_CLARITY,
                    "先给结论再展开，不绕圈",
                    "发言首句是否含明确观点句",
                    "按发言轮次中\"观点先行\"占比",
                    "读后感分享", "沉稳内敛观察者",
                    "你提问少而短（\"所以你觉得这本书怎么样？\"），逼对方必须先亮观点"),
            new SubSkill("concrete_wording", "用词具体", DIM_CLARITY,
                    "用具体名词/事例替代空泛表述",
                    "\"东西/挺好/差不多\"类空泛词密度 vs 具体细节",
                    "有无实例、细节、量化表述",
                    "读书交流", "不善交际慢热",
                    "你的回应平淡简短（\"哦这样\"），迫使对方补充具体细节才能续聊"),
            new SubSkill("claim_evidence", "观点-论据配对", DIM_LOGICALITY,
                    "每个主张有支撑",
                    "主张句后是否跟随因为/比如/数据等支撑",
                    "配对率+论据实质性（防空壳，同B防刷分）",
                    "读后感分享", "沉稳内敛观察者",
                    "对方每给一个观点，你追问一次\"为什么这么说？书里有依据吗？\""),
            new SubSkill("structure_linking", "结构连接", DIM_LOGICALITY,
                    "有效使用先后/因果连接组织多句",
                    "有效连接词使用（区别于堆叠\"然后\"，仲裁同B §2-5）",
                    "结构完整度",
                    "面试演练", "沉稳内敛观察者",
                    "你提多部分复合问题（\"先谈谈…再说说…最后…\"），要求结构化回答"),
            new SubSkill("self_consistency", "前后自洽", DIM_LOGICALITY,
                    "观点前后不矛盾",
                    "跨轮次观点比对",
                    "有无自我推翻/矛盾",
                    "面试演练", "幽默风趣社牛",
                    "你隔几轮回扣对方前面说过的话（\"你刚才不是说…？\"），检验前后一致"),
            new SubSkill("emotion_naming", "情绪命名", DIM_EMPATHY_LISTENING,
                    "识别并说出对方情绪",
                    "\"听起来你有点…/你应该挺…\"类句式且指向对方",
                    "命中且准确=正向；套话不加分",
                    "日常闲聊", "不善交际慢热",
                    "你在闲聊中含蓄流露 2~3 次低落/焦虑（用词克制不直说），等待对方察觉并命名"),
            new SubSkill("paraphrase", "复述确认", DIM_EMPATHY_LISTENING,
                    "用自己的话复述对方观点再推进",
                    "\"你的意思是…？/所以说你…\"",
                    "复述准确+带延展=正向；空洞附和不算（B §2 仲裁）",
                    "读书交流", "沉稳内敛观察者",
                    "你每次表达一个信息量足、有层次的完整观点，给对方复述确认的机会"),
            new SubSkill("empathic_followup", "共情追问", DIM_EMPATHY_LISTENING,
                    "追问指向对方内在体验",
                    "问感受/原因/经历类问题（区别于话题推动提问，归 interactivity）",
                    "追问深度与对方回应质量",
                    "初次见面", "乐观开朗自来熟",
                    "你主动分享带个人经历和感受的故事，留出可深挖的感受点"),
            new SubSkill("active_questioning", "主动提问", DIM_INTERACTIVITY,
                    "用问题推动对话",
                    "征询性提问频次与质量（语义过滤，问号不直接计分）",
                    "被动应答 vs 主动发起比例",
                    "初次见面", "不善交际慢热",
                    "你从不主动抛话题、只作简短回应，对话存续完全依赖对方提问"),
            new SubSkill("topic_driving", "话题推动", DIM_INTERACTIVITY,
                    "主动开启/延展新话题",
                    "新话题发起次数、接话延展率",
                    "冷场后是否主动续接",
                    "日常闲聊", "不善交际慢热",
                    "你的回复趋于终结话题（\"还行\"\"挺好的\"），制造需要换话题的冷场"),
            new SubSkill("turn_giving", "发言权让渡", DIM_INTERACTIVITY,
                    "把话筒递给对方",
                    "邀请式结尾（\"你觉得呢/你呢\"）、发言占比均衡",
                    "独白>80%且无邀请=低分（同B）",
                    "读书交流", "幽默风趣社牛",
                    "你话密健谈容易占满话筒，给对方练习\"拉回平衡/邀请你发言\"的机会"),
            new SubSkill("silence_handling", "冷场处理", DIM_INTERACTIVITY,
                    "对话停滞时的化解动作",
                    "对方短回复/停滞后，用户下一轮是否换角度/自嘲/提问",
                    "化解成功率",
                    "日常闲聊", "不善交际慢热",
                    "你在 2~3 处给出极短回应或明显停顿（\"…嗯\"），制造需要化解的冷场"),
            new SubSkill("less_hedging", "减少道歉退缩词", DIM_RELAXATION,
                    "少用\"可能我不对/随便说说/不好意思\"铺垫",
                    "退缩/道歉词密度（每百字<2不扣，口径同B）；道歉词只进本项（B §2-3 仲裁）",
                    "有观点敢直说",
                    "面试演练", "沉稳内敛观察者",
                    "你用中性追问施压（\"你确定？\"\"这个数据从哪来的？\"），不否定但要求立场清晰"),
            new SubSkill("self_disclosure", "敢于自我表露", DIM_RELAXATION,
                    "主动分享个人经历/感受/脆弱",
                    "第一人称经历句、感受句占比",
                    "纯附和/纯沉默=低分（B防刷分）",
                    "初次见面", "乐观开朗自来熟",
                    "你先做 1~2 次适度自我表露（糗事/近况烦恼），营造对等表露的安全氛围"),
            new SubSkill("non_defensive", "被质疑不防御", DIM_RELAXATION,
                    "面对异议时解释而非反击/收回",
                    "对方质疑后：坚持+论证=高；反击/立刻收回=低",
                    "需对方质疑轮次出现才可评，否则输出null",
                    "读后感分享", "幽默风趣社牛",
                    "你对对方观点提出 1~2 次温和但明确的异议（\"我倒觉得…你不觉得吗？\"）")
    );

    private static final Map<String, SubSkill> MAP =
            ALL.stream().collect(Collectors.toMap(SubSkill::key, Function.identity()));

    private static final Map<String, String> CN_TO_KEY =
            ALL.stream().collect(Collectors.toMap(SubSkill::cnName, SubSkill::key));

    private SubSkillConstants() {}

    /** 白名单校验（LLM 输出键漂移探针、任务构建校验共用）。 */
    public static boolean isValid(String key) {
        return key != null && MAP.containsKey(key);
    }

    /**
     * 宽容解析：LLM 偶尔用中文展示名代替短名（R3 联调实测：处方草稿输出「主动提问」）。
     * 命中短名或中文名 → 返回规范短名；否则 null。
     */
    public static String resolveKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (MAP.containsKey(s)) {
            return s;
        }
        return CN_TO_KEY.get(s);
    }

    public static SubSkill get(String key) {
        return MAP.get(key);
    }

    /** 按维度取子技能（dimension 用五维短名）。 */
    public static List<SubSkill> byDimension(String dimension) {
        return ALL.stream().filter(s -> s.dimension().equals(dimension)).toList();
    }

    public static String dimensionCn(String dimension) {
        return DIM_CN.getOrDefault(dimension, dimension);
    }

    /** 推荐映射行（设计文档 §4.2）；未知 key 返回 null。 */
    public static Recommendation recommendationFor(String key) {
        SubSkill s = MAP.get(key);
        if (s == null) {
            return null;
        }
        return new Recommendation(s.key(), s.cnName(), s.theme(), s.personality(), s.feed());
    }

    /**
     * 生成评分 prompt 的"子技能观察"判据段（P1-3 内嵌进 scorePrompt）。
     * 每子技能一行：- key 中文名：定义｜信号｜判据，按维度分组。
     */
    public static String promptBlock() {
        StringBuilder sb = new StringBuilder();
        for (String dim : List.of(DIM_CLARITY, DIM_LOGICALITY, DIM_EMPATHY_LISTENING,
                DIM_INTERACTIVITY, DIM_RELAXATION)) {
            sb.append("### ").append(dim).append(" 子技能\n");
            for (SubSkill s : byDimension(dim)) {
                sb.append("- ").append(s.key()).append(' ').append(s.cnName()).append('：')
                        .append(s.definition())
                        .append("｜信号：").append(s.signal())
                        .append("｜判据：").append(s.criteria())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    /** 兜底 sim 任务标题（处方生成失败/任务数不足时补齐用）。 */
    public static String defaultSimTaskTitle(String key, int target) {
        SubSkill s = MAP.get(key);
        return "完成" + target + "场针对「" + s.cnName() + "」的模拟训练";
    }

    /** 兜底 realworld 任务标题。 */
    public static String defaultRealTaskTitle(String key, int target) {
        SubSkill s = MAP.get(key);
        return "本周在真实对话中运用" + target + "次「" + s.cnName() + "」";
    }

    /** 兜底做法指导（微知识卡正文）。 */
    public static String defaultInstruction(String key) {
        SubSkill s = MAP.get(key);
        return "定义：" + s.definition() + "。可观察信号：" + s.signal() + "。判据：" + s.criteria() + "。";
    }
}
