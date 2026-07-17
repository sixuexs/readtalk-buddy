# Zero

本文件为 Coding agent 在此仓库中工作时提供指导。

## 构建与运行

```bash
# 后端 (Spring Boot 3.5.14, Java 17, Maven) — 需要 MongoDB 先运行
cd backend && ./mvnw spring-boot:run    # :8080
./mvnw test                             # 仅一个 contextLoads() 测试

# 前端 (uni-app 3.x, Vue 3 + TypeScript, pnpm) — 微信小程序
cd frontend && pnpm install
pnpm dev:mp-weixin                      # 开发构建 → 微信开发者工具导入 dist/dev/mp-weixin
pnpm build:mp-weixin                    # 生产构建
pnpm type-check                         # vue-tsc --noEmit (TS 4.9)
```

**MongoDB 必须先启动。** 数据库名 `readtalk_buddy`，无嵌入式 DB。MongoDB 未运行时后端无法启动。

## 架构

```
Frontend (uni-app Vue3, mp-weixin) ──HTTP──▶ Backend (:8080, Spring Boot 3.5, Java 17)
                                              ├── DeepSeek v4-flash (ChatClient)
                                              └── MongoDB localhost:27017 (readtalk_buddy)
```

### 后端目录结构

```
backend/src/main/java/com/backend/
  controller/   SimulationController — 所有 REST 端点 (/api/simulation/*)
  service/      SimulationService — 对话编排、prompt 构建、AI 评分
  agent/        5 个 ReactAgent (Spring AI Alibaba Graph):
                SimulationAgent, UserProfileAgent, IceBreakAgent, RelationAgent, CommAssistAgent
                每个 Agent 配一个 XxxTools 类 (@Tool 注解暴露给 LLM)
  document/     MongoDB @Document: ConversationDocument, UserProfileDocument, ContactDocument
  repository/   Spring Data MongoDB Repository (3 个)
  store/        ConversationStore — MongoDB CRUD 封装
  model/        Lombok @Data DTO: ApiResponse<T>, ChatMessage, StartRequest 等
  config/       CorsConfig — 允许所有来源跨域
  client/       空目录 — 预留
```

- **Multi-Agent 架构**：5 个特化 ReactAgent。Agent 间通过 **Spring Event** 松耦合通信（如 `ScoringCompletedEvent` → `UserProfileListener` 自动更新画像），共享 MongoDB 读写数据。无中心路由 Agent。
- **LLM**：DeepSeek v4-flash，通过 Spring AI `ChatClient` 调用。API key 及其他 LLM 配置在 `application.properties` 中。
- **ApiResponse<T>** 统一响应格式：`{ code: 0, data: ... }`，只有 `code` 和 `data` 两个字段（无 `message`）。

### 前端目录结构

```
frontend/src/
  pages/
    home/home.vue               占位页面 ("首页" 标题)
    social/social.vue           社交记录列表（完整实现，含 mock 数据降级）
    social/chat-history.vue     chat-history 子页面（不在 pages.json 中，通过 navigateTo 进入）
    relation/relation.vue       关系图谱（完整实现：力导向图 + 通讯录 + 详情抽屉 + 分析面板）
    simulation/simulation.vue   情景模拟（完整实现：配置选择 + 聊天界面 + 键盘适配）
    profile/profile.vue         个人中心（部分实现：菜单项占位）
  components/
    CustomTabBar.vue            5 个 tab 自定义导航栏（中间按钮凸起）
    FloatingActionButton.vue    可展开 "+" 悬浮按钮（扫码连接、沟通辅助）
    relation/                   关系页子组件 (RelationGraphChart, ContactList, PersonDrawer, InsightPanel, StarSearch)
  api/simulation.ts             所有 API 封装 (uni.request)
  types/simulation.ts           Message, Scenario, SessionSummary 等类型
  types/relation.ts             RelationContact, RelationViewMode
  types/relationInsight.ts      关系分析类型
  constants/freshTheme.ts       主题色、图例配色
  constants/relationCopy.ts     关系页文案常量
  utils/                        工具函数 (relationGraph, relationInsight, relationSearch, wx-canvas)
  data/mockRelations.ts         关系模拟数据
  pages.json                    页面路由 + tabBar 配置（非 Vue Router）
```

- **无状态管理库**（无 Pinia/Vuex）。每个页面用本地 `ref`/`reactive`。
- 目标平台 **微信小程序**。用 `<view>`、`<text>`、`<image>` 替代 HTML 标签。**禁止** `window`、`document`、`localStorage`、DOM API。
- API 基础地址硬编码 `http://localhost:8080` 在 `api/simulation.ts`。
- 自定义 tabBar 在 `pages.json` 中声明 `"custom": true`。

## API 端点

所有端点前缀：`/api/simulation`

| 模块 | 端点 | 方法 |
|------|------|------|
| 情景模拟 | `/config`, `/scenario`, `/start`, `/send`, `/history`, `/sessions`, `/score` | GET/POST |
| 用户画像 | `/profile`, `/profile/assess` | GET/POST |
| 关系运维 | `/contacts`, `/contacts/check`, `/contacts/{id}/intimacy`, `/contacts/{id}/drift`, `/contacts/{id}/recover` | GET/POST |
| 破冰分析 | `/icebreak` | POST |
| 沟通辅助 | `/assist/analyze`, `/assist/score` | POST |
| Agent管理 | `/agents`, `/agent` | GET/POST |

## 前后端命名约定

- **`scenarioId` = `sessionId`**：前端 `sendMessage({ scenarioId })` 传的是 `startSimulation` 返回的 sessionId。两者是同一个值。
- **API 响应解包**：`res.code === 0` → 使用 `res.data`。错误时 `catch` 用 `uni.showToast`。
- **MongoDB 消息存储**：消息以 `MessageItem` 内嵌在 `ConversationDocument.messages[]` 中，不是独立集合。每条消息有 `role` ("self" | "other")、`content`、`timestamp`（epoch ms）。

## 前端强制要求

### 每个 tab 页面必须包含
```vue
<CustomTabBar />
<FloatingActionButton />
```
漏掉 → 底部导航栏消失。CustomTabBar 接管系统 tabBar。

### FAB 与 TabBar 联动
CustomTabBar 点击时 emit `uni.$emit('tab-switch')`，FAB 监听 `uni.$on('tab-switch')` 自动收起。这是通过 uni 全局事件总线实现的。

### 聊天页键盘适配（simulation.vue）
输入框设置 `:adjust-position="false"`，用 `uni.onKeyboardHeightChange` 获取键盘高度，动态调整 `position: fixed` 的 input bar 的 `bottom` 值。**不要改这个模式。**

## 后端注意事项

### `application.properties` 被 gitignore
需手动创建：
```properties
spring.application.name=backend
server.port=8080
deepseek.api.key=sk-...
deepseek.api.url=https://api.deepseek.com
deepseek.api.model=deepseek-v4-flash
deepseek.api.temperature=0.8
deepseek.api.max-tokens=1024
spring.ai.zhipuai.chat.enabled=false
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=readtalk_buddy
```

### 评分解析 (scoreConversation)
LLM 返回纯 JSON，Jackson 解析。JSON 必须包含：`expression`, `affinity`, `logic`, `totalScore`（int）、`comment`（string）、`strengths`, `suggestions`（string[]）。解析失败 → `RuntimeException`。

### Agent 新增方式
1. 创建 `XxxTools.java`：`@Component` + `@Tool` 方法
2. 创建 `XxxAgent.java`：`@Component` + `implements Agent`
3. 自动发现注册 — 无需手动配置

## 模仿文件
- `frontend/src/data/mockRelations.ts` — 关系图谱演示数据
- 关系页组件在 `frontend/src/components/relation/` 下
