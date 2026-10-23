package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;

/** Spends a Spore on reattaching one lost arm or leg of the Unit with this id. */
public record ReattachActionDTO(Integer unitId, BodyPart part) {}
