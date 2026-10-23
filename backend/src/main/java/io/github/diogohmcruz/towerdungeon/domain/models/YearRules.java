package io.github.diogohmcruz.towerdungeon.domain.models;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * What the Year does, as pure functions of the tick within it. The danger at sea is worked out at
 * ticks 5, 15, 25 and so on and held for the ticks between, so it equals its extremes exactly at
 * the middle of Summer (tick 225) and of Winter (tick 525).
 */
public final class YearRules {
  private final GameProperties.Year rules;

  public YearRules(GameProperties.Year rules) {
    this.rules = rules;
  }

  public int ticksPerYear() {
    return rules.ticksPerYear();
  }

  public Quarter quarterAt(int tick) {
    var inYear = Math.floorMod(tick, rules.ticksPerYear());
    return Quarter.values()[inYear / Math.max(1, rules.getTicksPerQuarter())];
  }

  /** The first tick of the Quarter after the one {@code tick} is in (0 for the next Spring). */
  public int nextQuarterStart(int tick) {
    var per = Math.max(1, rules.getTicksPerQuarter());
    var inYear = Math.floorMod(tick, rules.ticksPerYear());
    return ((inYear / per + 1) * per) % rules.ticksPerYear();
  }

  public double sanityDrain(Quarter quarter) {
    return quarter == Quarter.SPRING ? rules.getSpringSanityDrain() : 1d;
  }

  public double suppliesDrain(Quarter quarter) {
    return quarter == Quarter.SUMMER ? rules.getSummerSuppliesDrain() : 1d;
  }

  /** Never below 1: no Quarter lowers Food income. */
  public double fieldsYield(Quarter quarter) {
    return quarter == Quarter.AUTUMN ? Math.max(1d, rules.getAutumnFieldsYield()) : 1d;
  }

  public double attrition(Quarter quarter) {
    return quarter == Quarter.WINTER ? rules.getWinterAttrition() : 1d;
  }

  /** The danger at sea for a tick of the Year, as a share between its two extremes. */
  public double seaRisk(int tick) {
    var period = rules.ticksPerYear();
    var refresh = Math.max(1, rules.getSeaRiskRefreshTicks());
    var offset = refresh / 2;
    var held = Math.floorDiv(Math.floorMod(tick, period) - offset, refresh) * refresh + offset;
    var angle = 2 * Math.PI * (held - rules.getSeaRiskPhaseTicks()) / period;
    return rules.getSeaRiskMean() - rules.getSeaRiskAmplitude() * Math.sin(angle);
  }
}
