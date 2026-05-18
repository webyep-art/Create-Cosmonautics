package dev.devce.rocketnautics.registry;

import com.tterrag.registrate.util.entry.BlockEntry;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.RocketBlockItem;
import dev.devce.rocketnautics.content.blocks.*;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.neoforged.neoforge.common.Tags;

import java.util.Map;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static com.simibubi.create.foundation.data.TagGen.tagBlockAndItem;

public class RocketBlocks {
    private static final SimulatedRegistrate REGISTRATE = RocketNautics.getRegistrate();

    public static final BlockEntry<RocketThrusterBlock> ROCKET_THRUSTER = REGISTRATE.block("rocket_thruster", RocketThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<VectorThrusterBlock> VECTOR_THRUSTER = REGISTRATE.block("vector_thruster", VectorThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<BoosterThrusterBlock> BOOSTER_THRUSTER = REGISTRATE.block("booster_thruster", BoosterThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<RCSThrusterBlock> RCS_THRUSTER = REGISTRATE.block("rcs_thruster", RCSThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<SeparatorBlock> SEPARATOR = REGISTRATE.block("separator", SeparatorBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<SputnikBlock> SPUTNIK = REGISTRATE.block("sputnik", SputnikBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .item(RocketBlockItem::new)
            .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                    .pattern("L L")
                    .pattern("IBI")
                    .pattern("L L")
                    .define('L', Items.LIGHTNING_ROD)
                    .define('I', Items.IRON_INGOT)
                    .define('B', Items.IRON_BLOCK)
                    .unlockedBy("has_iron_block", prov.has(Items.IRON_BLOCK))
                    .save(prov))
            .build()
            .register();

    public static final BlockEntry<HologramTableBlock> HOLOGRAM_TABLE = REGISTRATE.block("hologram_block", HologramTableBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .transform(pickaxeOnly())
            .item(RocketBlockItem::new)
            .build()
            .register();

    static { REGISTRATE.setCreativeTab(RocketTabs.WORLD_TAB); }

    public static final BlockEntry<Block> TITANIUM_ORE = REGISTRATE.block("titanium_ore", Block::new)
            .initialProperties(() -> Blocks.IRON_ORE)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_PINK)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE))
            .loot((lt, b) ->  {
                HolderLookup.RegistryLookup<Enchantment> enchantmentRegistryLookup = lt.getRegistries().lookupOrThrow(Registries.ENCHANTMENT);
                lt.add(b, lt.createSilkTouchDispatchTable(b, lt.applyExplosionDecay(b, LootItem.lootTableItem(RocketItems.RAW_TITANIUM.get())
                                        .apply(ApplyBonusCount.addOreBonusCount(enchantmentRegistryLookup.getOrThrow(Enchantments.FORTUNE))))));
            })
            .transform(pickaxeOnly())
            .tag(BlockTags.NEEDS_DIAMOND_TOOL)
            .tag(Tags.Blocks.ORES)
            .transform(tagBlockAndItem(Map.of(
                    RocketTags.MetalTags.TITANIUM.ores.blocks(), RocketTags.MetalTags.TITANIUM.ores.items(),
                    Tags.Blocks.ORES_IN_GROUND_STONE, Tags.Items.ORES_IN_GROUND_STONE)))
            .tag(Tags.Items.ORES)
            .build()
            .register();

    public static final BlockEntry<Block> DEEPSLATE_TITANIUM_ORE = REGISTRATE.block("deepslate_titanium_ore", Block::new)
            .initialProperties(() -> Blocks.DEEPSLATE_IRON_ORE)
            .properties(p -> p.mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE))
            .loot((lt, b) ->  {
                HolderLookup.RegistryLookup<Enchantment> enchantmentRegistryLookup = lt.getRegistries().lookupOrThrow(Registries.ENCHANTMENT);
                lt.add(b, lt.createSilkTouchDispatchTable(b, lt.applyExplosionDecay(b, LootItem.lootTableItem(RocketItems.RAW_TITANIUM.get())
                        .apply(ApplyBonusCount.addOreBonusCount(enchantmentRegistryLookup.getOrThrow(Enchantments.FORTUNE))))));
            })
            .transform(pickaxeOnly())
            .tag(BlockTags.NEEDS_DIAMOND_TOOL)
            .tag(Tags.Blocks.ORES)
            .transform(tagBlockAndItem(Map.of(
                    RocketTags.MetalTags.TITANIUM.ores.blocks(), RocketTags.MetalTags.TITANIUM.ores.items(),
                    Tags.Blocks.ORES_IN_GROUND_DEEPSLATE, Tags.Items.ORES_IN_GROUND_DEEPSLATE)))
            .tag(Tags.Items.ORES)
            .build()
            .register();

    static { REGISTRATE.setCreativeTab(RocketTabs.RESOURCE_TAB); }

    public static final BlockEntry<Block> RAW_TITANIUM_BLOCK = REGISTRATE.block("raw_titanium_block", Block::new)
            .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops())
            .transform(pickaxeOnly())
            .tag(Tags.Blocks.STORAGE_BLOCKS)
            .tag(BlockTags.NEEDS_IRON_TOOL)
            .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM.rawStorageBlocks))
            .tag(Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();

    public static final BlockEntry<Block> TITANIUM_BLOCK = REGISTRATE.block("titanium_block", Block::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops())
            .transform(pickaxeOnly())
            .tag(BlockTags.NEEDS_IRON_TOOL)
            .tag(Tags.Blocks.STORAGE_BLOCKS)
            .tag(BlockTags.BEACON_BASE_BLOCKS)
            .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM.storageBlocks))
            .tag(Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();

    public static final BlockEntry<Block> TITANIUM_ALLOY_BLOCK = REGISTRATE.block("titanium_alloy_block", Block::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_PINK)
                    .requiresCorrectToolForDrops())
            .transform(pickaxeOnly())
            .tag(BlockTags.NEEDS_IRON_TOOL)
            .tag(Tags.Blocks.STORAGE_BLOCKS)
            .tag(BlockTags.BEACON_BASE_BLOCKS)
            .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM_ALLOY.storageBlocks))
            .tag(Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();

    static { REGISTRATE.setCreativeTab(null); }


    public static void init() {}
}
