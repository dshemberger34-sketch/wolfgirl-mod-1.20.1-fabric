package com.wolfgirl;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public class WolfGirlMod implements ModInitializer {
    public static final String MOD_ID = "wolfgirl";

    public static final EntityType<WolfGirlEntity> WOLF_GIRL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "wolf_girl"),
            EntityType.Builder.<WolfGirlEntity>create(WolfGirlEntity::new, SpawnGroup.CREATURE)
                    .setDimensions(0.8f, 2.3f)
                    .maxTrackingRange(10)
                    .build("wolf_girl"));

    public static final Item WOLF_GIRL_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "wolf_girl_spawn_egg"),
            new SpawnEggItem(WOLF_GIRL, 0xE9B58C, 0x1E1E1E, new Item.Settings()));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(WOLF_GIRL, WolfGirlEntity.createWolfGirlAttributes());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(WOLF_GIRL_SPAWN_EGG));

        // Natural spawning in forests and taigas, like wolves.
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(
                        BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA,
                        BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.GROVE),
                SpawnGroup.CREATURE, WOLF_GIRL, 6, 1, 2);

        SpawnRestriction.register(WOLF_GIRL, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
    }
}
