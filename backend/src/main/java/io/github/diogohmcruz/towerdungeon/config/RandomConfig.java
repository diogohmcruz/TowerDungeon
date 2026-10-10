package io.github.diogohmcruz.towerdungeon.config;

import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RandomConfig {
  @Bean
  public RandomGenerator randomGenerator() {
    return new ThreadLocalRandomGenerator();
  }
}
