# Zero.md

本文件为  Coding agent 在此仓库中工作时提供指导。

## 项目概述

阅谈智伴 (ReadTalk Buddy) 是一款全栈式社交沟通训练应用。用户通过 AI 驱动的情景模拟来练习对话技巧。后端调用 DeepSeek API 生成 NPC 对话；前端是基于 uni-app 的跨端应用，目标平台为微信小程序、H5 和原生 App。

## 构建与运行

### 后端 (Spring Boot 3.5.14, Java 17, Maven)

```bash
cd backend
./mvnw spring-boot:run      # 启动在 :8080 端口
./mvnw test                   # 单个上下文加载测试
```

### 前端 (uni-app 3.x, Vue 3 + TypeScript, pnpm)

```bash
cd frontend
pnpm install
pnpm dev:mp-weixin            # 微信小程序开发构建（主要目标平台）
pnpm build:mp-weixin          # 微信小程序生产构建
pnpm type-check               # vue-tsc --noEmit
```

`dev:mp-weixin` 执行后，将 `frontend/dist/dev/mp-weixin` 目录导入微信开发者工具。

## 架构

```
backend/                       # Spring Boot REST API
  src/main/java/com/backend/
    controller/                # @RestController — 唯一控制器: SimulationController (/api/simulation)
    service/                   # 业务逻辑 — SimulationService 构建 prompt、编排对话流程
    client/                    # DeepSeekClient — 基于 Spring RestClient 的 OpenAI 兼容 HTTP 客户端
    model/                     # Lombok @Data DTO: ApiResponse<T>, ChatMessage, StartRequest 等
    store/                     # ConversationStore — ConcurrentHashMap（无数据库，会话存于内存）
    config/                    # CorsConfig（允许所有来源）
src/main/resources/
  application.properties       # 服务端口、DeepSeek API key/URL/model/temperature/max-tokens
```

- **无数据库、无鉴权。** 会话状态存储在 `ConcurrentHashMap` 中，重启后丢失。
- 所有接口统一使用 `ApiResponse<T>` 封装响应（code + message + data）。
- DeepSeekClient 支持 `thinking` 模式开关和 `reasoning-effort` 参数。

```
frontend/                      # uni-app (Vue 3 Composition API + TypeScript)
  src/
    pages/                     # 5 个 tab 页面（home, social, relation, simulation, profile）
      simulation/simulation.vue  # 已完整实现 — 配置选择 + 聊天界面
      home/home.vue              # 占位页面
      social/social.vue          # 占位页面
      relation/relation.vue      # 占位页面
      profile/profile.vue        # 部分实现 — 菜单项带有占位处理函数
    components/
      CustomTabBar.vue         # 5 个 tab 的自定义底部导航栏，中间按钮凸起
      FloatingActionButton.vue # 可展开的 "+" 悬浮按钮，包含两个子按钮
    api/simulation.ts          # 所有 5 个 /api/simulation 接口的 uni.request() 封装
    types/simulation.ts        # 情景模拟相关的 TypeScript 接口定义
    pages.json                 # 页面路由 + tabBar 配置（非 Vue Router）
    manifest.json              # uni-app 清单文件（mp-weixin、app-plus、h5 配置）
```

- **无状态管理库**（无 Pinia/Vuex）。每个页面使用本地 `ref`/`reactive` 管理状态。
- API 基础地址硬编码为 `http://localhost:8080`，位于 `api/simulation.ts`。
- 自定义 tabBar 在 `pages.json` 中声明（`"custom": true`），由 `CustomTabBar.vue` 实现。
- 页面路由使用 uni-app 内置导航 API（`uni.switchTab`、`uni.navigateTo` 等），而非 Vue Router。
- 目标平台为微信小程序。使用 `<view>`、`<text>`、`<image>` 代替 HTML 标签。不要使用 `window`、`document`、`localStorage` 或 DOM API。

## 代码规范

- **Java DTO**：使用 Lombok `@Data`。字段名使用与中文含义对应的英文（如 `themeName`、`personalityDesc`）。
- **Vue 页面**：统一使用 `<script setup lang="ts">` 搭配 Composition API。
- **API 调用**：在 `api/simulation.ts` 中通过 `uni.request()` 封装。遵循现有的 try/catch + `uni.showToast` 错误处理模式。
- **响应解包**：始终解包 `ApiResponse<T>` — 先检查 `res.data.code === 200`，再使用 `res.data.data`。


