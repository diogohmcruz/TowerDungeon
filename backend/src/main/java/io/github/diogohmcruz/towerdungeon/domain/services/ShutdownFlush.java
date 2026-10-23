package io.github.diogohmcruz.towerdungeon.domain.services;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Writes every attached Campaign when the application stops, within a bounded wait. It stops before
 * the web server (the highest phase stops first), and it does not rely on graceful shutdown.
 */
@Component
@RequiredArgsConstructor
public class ShutdownFlush implements SmartLifecycle {
  private final GameService gameService;
  private volatile boolean running;

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    gameService.shutdown();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }
}
