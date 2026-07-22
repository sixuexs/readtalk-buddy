# 阅谈智伴 · E 模块 — 关系运维引擎

> **版本**：2026-07-23
> **职责**：亲密度计算、关系预警、个性化建议生成三大核心链路。
> **代号 E**：Engagement（关系运维），替代旧 `RelationTools` 硬编码算法。

---

## 0. 概述

E 模块将原 `RelationTools` 中散落的亲密度计算、预警判断、LLM 建议三个职责拆为独立服务，每层可独立测试、独立降级。

### 模块架构

```
RelationTools (C5 — 5 个 @Tool 方法, API 层)
  ├── IntimacyService (C2) — 亲密度计算引擎 + 双写收口
  ├── WarningService (C3) — 预警规则引擎
  └── RelationAdviceService (C4) — LLM 建议生成器
       └── 常量层: IntimacyConstants + WarningConstants (C1)
```

### 分层职责

| 层级 | 代号 | 职责 | 有无副作用 |
|------|------|------|-----------|
| 常量层 | C1 | 权重 / 阈值 / 衰减参数统一源 | 无 |
| 计算引擎 | C2 | 四分量公式 + 双写 (MySQL + MongoDB) | 有（写库） |
| 规则引擎 | C3 | 全量预警扫描 + 单联系人检测 | 有（写预警标记） |
| 生成器 | C4 | LLM 个性化建议 + 规则 Fallback | 无（纯文本） |
| API 层 | C5 | 5 个 @Tool 方法, 委托引擎 | 聚合协调 |

---

## 1. 常量层 (C1)

### 1.1 IntimacyConstants

亲密度公式的四分量权重，总和 = 1.0：

```java
W_TTL     = 0.35  // 时效（lastContactDays 指数衰减）
W_FREQ    = 0.25  // 频率（interaction_meta count30d；P3=空 → 降级=0）
W_DEPTH   = 0.20  // 深度（avg msg/conv；P1=无关联 → 降级=空）
W_QUALITY = 0.20  // 质量（avg 5D score；P1=无关联 → 降级=空）
```

亲密度环阈值：

| 区间 | 阈值 | 含义 |
|------|------|------|
| 核心区（内环） | `≥70` | CORE_THRESHOLD |
| 中间区（中环） | `≥40` | MID_THRESHOLD |
| 边缘区（外环） | `<40` | 无需显式常量 |

TTL 衰减参数：

| 参数 | 值 | 含义 |
|------|-----|------|
| TTL_HALFLIFE_DAYS | 30.0 | 半衰期（天），`0.5^(days/30)` |
| TTL_FLOOR | 0.10 | 最低衰减系数，衰减到 10% 不再下降 |

降级默认值（对应 P1/P3 空数据通道）：

```java
DEGRADE_FREQ_SCORE  = 0.0  // 频率分量降级
DEGRADE_DEPTH_SCORE = 0.0  // 深度分量降级
DEGRADE_QUALITY_SCORE = 0.0 // 质量分量降级
FALLBACK_INTIMACY   = 10   // 全降级兜底分 = round(TTL_FLOOR × 100)
```

**实现文件**：`backend/…/constant/IntimacyConstants.java`

### 1.2 WarningConstants

预警阈值：

| 常量 | 值 | 含义 |
|------|-----|------|
| DRIFT_INTIMACY_THRESHOLD | 40 | 亲密度低于此值触发疏远预警 |
| SEVERE_DRIFT_THRESHOLD | 20 | 严重疏远阈值 |
| DRIFT_DAYS_THRESHOLD | 30 | 超过此天数未联系触发预警 |
| REMINDER_DAYS_THRESHOLD | 14 | 长期未联系提醒阈值（14-30天） |

质量预警（P1 降级中）：

| 常量 | 值 | 含义 |
|------|-----|------|
| QUALITY_DECLINE_THRESHOLD | 0.4 | 质量分持续下降低于阈值触发预警 |
| QUALITY_WINDOW_SIZE | 5 | 质量预警观察窗口（会话数） |

抑制参数：

| 常量 | 值 | 含义 |
|------|-----|------|
| SUPPRESS_DAYS | 30 | 用户拒绝挽救后预警抑制天数 |

**实现文件**：`backend/…/constant/WarningConstants.java`

---

## 2. IntimacyService (C2) — 亲密度计算引擎

### 2.1 公式

```
intimacy = round(时效 × W_TTL + 频率 × W_FREQ + 深度 × W_DEPTH + 质量 × W_QUALITY)
```

结果约束 `[0, 100]`。

### 2.2 当前实现态（第一版降级）

由于 P1（conversations/contacts 无关联字段）和 P3（interaction_meta 无写入），当前仅有**时效分量**贡献分：

```
仅时效分 = round(ttl_decay(lastContactDays) × 100 × 0.35)
```

其中 `ttl_decay(days) = max(0.5^(days/30), 0.10)`。

不同 `lastContactDays` 下的预期值：

| lastContactDays | ttl_decay | 时效分量 ≈ | 总亲密 ≈ |
|-----------------|-----------|------------|---------|
| 0 | 1.00 | 35.0 | 35 |
| 7 | 0.85 | 29.8 | 30 |
| 14 | 0.72 | 25.2 | 25 |
| 30 | 0.50 | 17.5 | 18 |
| 60 | 0.25 | 8.8 | 9 |
| 90 | 0.13 | 4.6 | 5 |
| 120+ | 0.10 | 3.5 | 3 |

### 2.3 双写收口：persistIntimacy

`persistIntimacy(userId, contactId, score)` 是 E 模块唯一写库收口方法，执行两步：

```
(a) MySQL  contact.intimacy_score = score   ← 度量字段写主
(b) MongoDB contacts.intimacy = score        ← 前端读存值（不发 ContactSavedEvent）
```

规则：
- 不触发 `ContactSavedEvent`（MongoDB 侧直接 `contactRepo.save()`，不走 `ContactService.saveOrUpdate`）
- `calcIntimacy`（单次实时）和 `refreshAllIntimacy`（定时批量）都只调此方法
- **TODO[阶段二]**：去掉 (b) Mongo 写回，MySQL 成为唯一度量源

### 2.4 批量入口：refreshAllIntimacy

```java
public void refreshAllIntimacy() {
    List<ContactDocument> contacts = contactRepo.findAll();  // 全量扫描
    for (var c : contacts) {
        int score = calculateIntimacy(c);
        persistIntimacy(0L, c.getId(), score);  // TODO[P2]: userId 传 0L
    }
}
```

- 定时任务入口，无 userId（当前传 `0L`，见 P2）
- 供 `@Scheduled` 或手动触发

### 2.5 事件骨架：onScoringCompleted

```java
@EventListener(AgentEvent.ScoringCompleted.class)
public void onScoringCompleted(AgentEvent.ScoringCompleted event) {
    // TODO[P1+P3]: 管道通后启用全四分量刷新
}
```

评分完成后自动触发，当前为骨架（空壳），待 P1+P3 管道通后启用。

**实现文件**：`backend/…/service/IntimacyService.java`

---

## 3. WarningService (C3) — 预警规则引擎

### 3.1 checkAllContacts（全量扫描）

返回两个列表：

**提醒列表（reminders）**：

| 类型 | 触发条件 | 说明 |
|------|---------|------|
| 生日提醒 | `birthday != null` 且距生日 ≤7 天 | 含天数 + 建议文案 |
| 联系提醒 | 14 < lastContactDays ≤ 30 | 已 X 天未联系，建议问候 |

**预警列表（warnings）**：

| 类型 | 触发条件 | 抑制条件 | 说明 |
|------|---------|---------|------|
| 疏远预警 | `intimacy < 40` | `suppressWarning || recovering` | 关系正在疏远 |

严重度分级：

| 亲密度 | 严重度 |
|--------|--------|
| `<20` | 严重（SEVERE_DRIFT_THRESHOLD） |
| `20-40` | 中等 |
| `≥40` | 不预警 |

### 3.2 detectDrift（单联系人检测）

判断条件：`intimacy < 40 OR lastContactDays > 30`。

若判定为 drifting 且 `contact.warning == false`，则设置 `warning=true` 并记录 `warningTime`。

返回值含 `isDrifting`（布尔）+ `severity`（严重/中等/轻微）。

### 3.3 质量预警

当前跳过（TODO[P1]）。设计意图：当质量分（5 维评分均值）在 `QUALITY_WINDOW_SIZE` 次会话内持续下降低于 `QUALITY_DECLINE_THRESHOLD` 时触发。

**实现文件**：`backend/…/service/WarningService.java`

---

## 4. RelationAdviceService (C4) — LLM 建议生成器

### 4.1 架构

- 独立 `ChatClient` 实例（通过 `ChatClient.Builder` 注入），不共享其他服务的 LLM 连接
- 纯文本生成，无副作用（冷却/挽救状态由 `RelationTools` 控制）
- 生成失败时走规则 `fallback`，不抛异常

### 4.2 输入输出 Schema

**AdviceContext**（由 RelationTools 组装）：

| 字段 | 类型 | 说明 |
|------|------|------|
| daysSinceLastContact | int | 上次联系距今天数 |
| intimacyScore | int | 当前亲密度 |
| warningType | String | "疏远预警" 或 null |
| warningLevel | String | "RED" / "ORANGE" / "YELLOW"（由 severity 映射） |

**AdviceResult**（LLM 输出 / Fallback 同 schema）：

| 字段 | 类型 | 说明 |
|------|------|------|
| entryTopics | `List<String>` | 切入点话题列表 |
| openingLine | String | 开场白 |
| cautions | `List<String>` | 注意事项 |
| recoverSteps | `List<String>` | 挽救步骤（2-3 条） |
| expectation | String | 预期效果一句话 |

### 4.3 硬约束

Prompt 中有 4 条硬约束确保输出质量：

1. `entryTopics` 至少 1 条显式引用联系人的兴趣爱好 / 性格 / 身份标签
2. 若用户有不足（`topWeaknesses` 非空），给具容话术而非抽象要求
3. 语气按预警级别：RED = 强调关系价值、ORANGE = 明确建议具体行动、YELLOW = 轻松提醒
4. 禁止居高临下或制造焦虑

### 4.4 Fallback 兜底

当 LLM 调用失败（网络/解析/超时），`fallback(ContactDocument)` 用规则生成同 schema 结果：

```
entryTopics  → 基于 interests 列表产生问候式话题
openingLine  → "好久不见，最近还好吗？"
cautions     → 性格含内向/敏感时追加温和提示
recoverSteps → ["每隔几天发一次消息，保持自然频率", "关注对方朋友圈，适时互动"]
expectation  → "重新建立联系，让关系回归自然状态"
```

### 4.5 输入数据源

| 数据 | 来源 | 说明 |
|------|------|------|
| contact | MongoDB ContactDocument | 姓名、关系类型、兴趣、标签、性格 |
| profile | MongoDB UserProfileDocument | topStrengths、topWeaknesses（P4=存在） |
| ctx | RelationTools 组装 | 天数、分数、类型、级别 |

**实现文件**：`backend/…/service/RelationAdviceService.java`

---

## 5. RelationTools (C5) — API 层改造

5 个 `@Tool` 方法的引擎替换对照：

| 方法 | 参数 | 引擎 | 变更说明 |
|------|------|------|---------|
| listContacts | — | 存储值 (MongoDB) | 无变化，仍直接读 ContactRepository |
| calcIntimacy | contactId | IntimacyService | 替换硬编码算法，委托 `calculateIntimacy` + `persistIntimacy` 双写 |
| checkMaintenance | — | WarningService | 委托 `checkAllContacts()` 全量扫描 |
| detectDrift | contactId | WarningService | 委托 `detectDrift(contact)` 单联系人检测 |
| generateRecoverPlan | contactId, chooseRecover | RelationAdviceService | 移除内联 ChatClient，委托独立 LLM 生成器 |

### generateRecoverPlan 流程

```
用户选择不挽救：
  → setSuppressWarning(true) + setWarning(false) + setRecovering(false)
  → 返回 status=suppressed

用户选择挽救：
  → WarningService.detectDrift(contact) 获取上下文
  → UserProfileRepository 获取画像（TODO[多用户]：改 findByUserId）
  → RelationAdviceService.generatePersonalizedAdvice(contact, profile, ctx)
  → 设置 recovering=true
  → 返回 status=recovering + plan
```

**实现文件**：`backend/…/agent/RelationTools.java`

---

## 6. ContactService 白名单映射（定点 3 修复）

`ContactService.saveOrUpdate(ContactDocument, Long)` 按字段分写主，确保 MongoDB Agent 写入不回传覆盖 MySQL 度量字段。

**档案字段白名单**（MongoDB → MySQL 单向同步）：

```
name, avatarUrl, relationType, personality, interests, labels, updatedAt
```

**度量字段黑名单**（永不触碰）：

```
intimacyScore, lastContactTime, warningDismissedAt, deletedAt, id, createdAt
```

**按字段分写主总览**：

| 字段类别 | 写主 | 同步方向 |
|---------|------|---------|
| 档案字段（白名单） | MongoDB ContactDocument | M→S 单向 |
| 度量字段（黑名单） | MySQL ContactEntity | 无同步，各自独立 |
| 元数据字段 | MySQL ContactEntity | JPA/DB 管理 |

**实现文件**：`backend/…/service/ContactService.java`

---

## 7. 已知限制（P1-P3, P6）

| 编号 | 描述 | 影响 | 当前处理 |
|------|------|------|----------|
| P1 | conversations/contacts 无关联字段 | 深度/质量分量 = 0，亲密度仅时效分量 | 降级：`calculateIntimacy` 跳过深度/质量 |
| P2 | conversations 无 userId | `refreshAllIntimacy` 传 `userId=0L` | MySQL 跳过（找不到匹配行），MongoDB 正常写入 |
| P3 | interaction_meta 无写入 | 频率分量 = 0，不计交流频率 | 降级：`DEGRADE_FREQ_SCORE = 0` |
| P6 | InteractionRecord 无 sessionId | 互动追踪缺失，不用作计算依据 | 未使用，不影响当前降级计算 |

### 降级态下的亲密度特性

当前仅 `时效(TTL) × W_TTL` 贡献分：

```
score = round(max(0.5^(days/30), 0.10) × 100 × 0.35)
      = round(ttl_decay × 35)
```

- 当天联系的人 ≈ 35 分（最高值，低于 CORE 阈值 70）
- 30 天未联系 ≈ 18 分（低于 DRIFT_INTIMACY_THRESHOLD 40，触发预警）
- 60 天未联系 ≈ 9 分
- 120 天以上 ≈ 3 分（TTL_FLOOR 保护，不清零）

### P1 修复后预期

关联 `contactId` 挂载后，深度/质量分量启用：

```
score = round( 时效×0.35 + 频率×0.25 + 深度×0.20 + 质量×0.20 )
```

各分量 0-100 归一化后加权，总分可覆盖 0-100 全范围，核心联系人的亲密度可突破 40+。

---

## 8. TODO 清单

### P1 级（深度/质量启用）

- **TODO[P1]**: conversations 挂载 contactId → 启用深度/质量分量
  - `onScoringCompleted` 中通过 sessionId → conversations doc → contactId
  - 读取 `evaluation_record` 或 `conversations.Evaluation` 的 5 维分作为质量分量
  - 读取 `conversation_detail` 消息数作为深度分量

### P2 级（多用户支持）

- **TODO[P2]**: conversations 加 userId → 修正 `refreshAllIntimacy` 调用
  - `persistIntimacy(0L, ...)` → `persistIntimacy(userId, ...)`
  - `findFirstByOrderByUpdatedAtDesc` → `findByUserId(userId)`

### P3 级（频率启用）

- **TODO[P3]**: interaction_meta 写入管道 → 启用频率分量
  - `calculateIntimacy` 中查询 `interaction_meta` 近 30 天条数
  - 归一化后 × W_FREQ 加入总分

### P6 级（互动追踪）

- **TODO[P6]**: InteractionRecord 加 sessionId 关联
  - 打通 conversation_detail 与 interaction_meta 的关联

### 表达层

- **TODO[表达层完整化]**: `generateRecoverPlan` 拆 `mode=light/full`
  - light：预警点开时触发，短建议
  - full：用户选择挽救时触发，完整方案
  - 当前 /recover 入口走 full，light 入口未实现

### 多用户

- **TODO[多用户]**: `findFirstByOrderByUpdatedAtDesc` → `findByUserId(userId)`
  - 当前取第一个画像（不限用户），多用户场景下会错配
  - 与 conversations 加 userId 同期修

---

## 附：文件索引

| 文件 | 层级 | 说明 |
|------|------|------|
| `constant/IntimacyConstants.java` | C1 | 四分量权重、环阈值、TTL 衰减参数 |
| `constant/WarningConstants.java` | C1 | 预警阈值、抑制参数 |
| `service/IntimacyService.java` | C2 | 亲密度计算 + 双写收口 + 事件骨架 |
| `service/WarningService.java` | C3 | 全量扫描 + 单联系人疏远检测 |
| `service/RelationAdviceService.java` | C4 | LLM 建议生成器 + 规则 Fallback |
| `agent/RelationTools.java` | C5 | 5 个 @Tool 方法，聚合引擎调用 |
| `service/ContactService.java` | — | 白名单映射（按字段分写主） |
