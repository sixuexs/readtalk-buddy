package com.backend.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 注册中心 —— 弱化的中心 Agent
 * <p>
 * 职责：注册发现 + 共享配置 + 事件总线。不做路由和工作流编排。
 * 所有实现 Agent 接口的 Spring Bean 自动被发现并注册。
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public AgentRegistry(List<Agent> agentList, ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        for (Agent agent : agentList) {
            agents.put(agent.name(), agent);
        }
    }

    @PostConstruct
    public void init() {
        log.info("AgentRegistry 初始化完成，已注册 {} 个 Agent:", agents.size());
        agents.forEach((name, agent) ->
                log.info("  [{}] {} — {}", name, agent.getClass().getSimpleName(), agent.description()));
    }

    /** 按名称获取 Agent */
    public Optional<Agent> get(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    /** 获取所有已注册 Agent */
    public Collection<Agent> all() {
        return Collections.unmodifiableCollection(agents.values());
    }

    /** 获取 Agent 数量 */
    public int count() {
        return agents.size();
    }

    /** 发布事件（Agent 间松耦合通信） */
    public void publishEvent(Object event) {
        eventPublisher.publishEvent(event);
    }
}
