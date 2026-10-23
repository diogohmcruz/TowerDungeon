package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * The payload of {@code SAVE_LOADOUT}: the slot to save in and the Loadout, as {@code INVADE} sends
 * it. Null is none or off.
 */
public record SaveLoadoutActionDTO(
    Integer slot,
    Integer salt,
    Integer saltPack,
    UnitStats saltBearer,
    Boolean panacea,
    Boolean overreach) {}
