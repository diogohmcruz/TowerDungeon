package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * A Unit that fell: its name, its Unit Type (the {@link UnitStats} name) and the Floor where it
 * fell. Floor 0 is the Village, for a Reserve lost to Starvation.
 */
public record Fallen(String name, String type, int floor) {}
