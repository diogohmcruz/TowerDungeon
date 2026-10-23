package io.github.diogohmcruz.towerdungeon.agent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Marks a bean of the agent surface: it exists only when {@code game.agent.enabled=true}, which
 * only the {@code agent} and {@code agent-stdio} profiles (or a test) set.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(prefix = "game.agent", name = "enabled", havingValue = "true")
public @interface AgentEnabled {}
