package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.VoyageGoods;

public record SendVoyageActionDTO(Integer relics, VoyageGoods goods) {}
