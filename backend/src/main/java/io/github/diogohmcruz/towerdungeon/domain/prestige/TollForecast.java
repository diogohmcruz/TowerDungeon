package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * What ending the Campaign now would pay, for the Toll bar. Sent with every frame while the
 * Campaign is active.
 *
 * @param toll the full breakdown of the Toll as it stands
 * @param pointsDivisor the Toll that pays one point of Legacy
 * @param nextLegacyAt the Toll at which the next whole point of Legacy is paid
 * @param exodusMinimum the Legacy the forecast must reach before Exodus is offered
 * @param canExodus whether Exodus is open now: playing, no Expedition active or Held, and the
 *     forecast at least {@code exodusMinimum}
 */
public record TollForecast(
    Toll toll, double pointsDivisor, double nextLegacyAt, int exodusMinimum, boolean canExodus) {}
