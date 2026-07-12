package com.backend.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;

/**
 * 阅谈智伴 Agent 统一接口 —— 所有特化 Agent 必须实现
 * <p>
 * 上层代码通过 AgentRegistry 获取 Agent，面向本接口编程，
 * 不依赖具体 Agent 实现，保证后续功能扩展的兼容性。
 */
public interface Agent {

    /** Agent 唯一标识，如 "simulation-agent" */
    String name();

    /** Agent 能力描述 */
    String description();

    /** 底层 ReactAgent 实例（Spring AI Alibaba） */
    ReactAgent reactAgent();
}
