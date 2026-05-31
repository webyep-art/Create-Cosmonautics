package dev.devce.rocketnautics.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.RocketBlockItem;
import dev.devce.rocketnautics.content.blocks.*;
import dev.devce.rocketnautics.content.blocks.world.MossBlock;
import dev.devce.rocketnautics.content.blocks.world.RockBlock;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.NonNull;

import java.util.Map;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static com.simibubi.create.foundation.data.TagGen.tagBlockAndItem;
import static dev.devce.rocketnautics.registry.RocketTags.BlockTags.GENERIC_CARVABLE;

public class RocketBlocks {
    private static final SimulatedRegistrate REGISTRATE = RocketNautics.getRegistrate();

    public static final BlockEntry<RocketThrusterBlock> ROCKET_THRUSTER = REGISTRATE.block("rocket_thruster", RocketThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .transform(existingDirectionalModel("rocket_thruster"))
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<VectorThrusterBlock> VECTOR_THRUSTER = REGISTRATE.block("vector_thruster", VectorThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .transform(existingDirectionalModel("vector_thruster_base"))
            .item(RocketBlockItem::new)
            .transform(RocketItems.noGeneratedModel())
            .build().register();

    public static final BlockEntry<BoosterThrusterBlock> BOOSTER_THRUSTER = REGISTRATE.block("booster_thruster", BoosterThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .transform(existingDirectionalModel("booster_thruster"))
            .item(RocketBlockItem::new).build().register();

    public static final BlockEntry<RCSThrusterBlock> RCS_THRUSTER = REGISTRATE.block("rcs_thruster", RCSThrusterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .tag(RocketTags.BlockTags.THRUSTERS.tag)
            .transform(existingDirectionalModel("rcs_thruster"))
            .item(RocketBlockItem::new).build().register();


    public static final BlockEntry<SeparatorBlock> SEPARATOR = REGISTRATE.block("separator", SeparatorBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .transform(existingDirectionalModel("sep"))
            .item(RocketBlockItem::new)
            .model((ctx, prov) -> prov.blockItem(ctx::getEntry, "_single"))
            .build().register();

    public static final BlockEntry<SputnikBlock> SPUTNIK = REGISTRATE.block("sputnik", SputnikBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .transform(existingSimpleModel("sputnik"))
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
            .transform(existingSimpleModel("hologram_block"))
            .item(RocketBlockItem::new)
            .transform(RocketItems.noGeneratedModel())
            .build()
            .register();

    public static final BlockEntry<dev.devce.rocketnautics.content.blocks.parachute.ParachuteCaseBlock> PARACHUTE_CASE = REGISTRATE.block("parachute_case", dev.devce.rocketnautics.content.blocks.parachute.ParachuteCaseBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .blockstate((ctx, prov) -> {})
            .item(RocketBlockItem::new)
            .transform(RocketItems.noGeneratedModel())
            .build()
            .register();

    public static final BlockEntry<Block> PARACHUTE_CAPSULE = REGISTRATE.block("parachute_capsule", Block::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(BlockBehaviour.Properties::noOcclusion)
            .transform(pickaxeOnly())
            .transform(existingSimpleModel("parachutecapsule"))
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

    // MOON START

    public static final BlockEntry<RotatedPillarBlock> LUNAR_REGOLITH = REGISTRATE.block("lunar_regolith", RotatedPillarBlock::new)
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .loot((tables, block) -> tables.add(block, tables.createSingleItemTableWithSilkTouch(block, RocketBlocks.LUNAR_SHATTERED_REGOLITH)))
            .tag(GENERIC_CARVABLE.tag)
            .transform(pickaxeOnly())
            .recipe((ctx, prov) -> prov.smeltingAndBlasting(DataIngredient.items((ItemLike) RocketBlocks.LUNAR_SHATTERED_REGOLITH), RecipeCategory.BUILDING_BLOCKS, ctx::getEntry, 1f))
            .blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry()))
            .item().build()
            .register();

    // TODO look into Create's block palette system
    public static final BlockEntry<StairBlock> LUNAR_REGOLITH_STAIRS = REGISTRATE.block("lunar_regolith_stairs", p -> new StairBlock(LUNAR_REGOLITH.getDefaultState(), p))
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(stairs(LUNAR_REGOLITH, "_end", "_side"))
            .item().build()
            .register();

    public static final BlockEntry<SlabBlock> LUNAR_REGOLITH_SLAB = REGISTRATE.block("lunar_regolith_slab", SlabBlock::new)
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(slab(LUNAR_REGOLITH, "_end", "_side"))
            .item().build()
            .register();

    public static final BlockEntry<Block> LUNAR_SHATTERED_REGOLITH = REGISTRATE.block("lunar_shattered_regolith", Block::new)
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .item().build()
            .register();

    public static final BlockEntry<StairBlock> LUNAR_SHATTERED_REGOLITH_STAIRS = REGISTRATE.block("lunar_shattered_regolith_stairs", p -> new StairBlock(LUNAR_SHATTERED_REGOLITH.getDefaultState(), p))
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(stairs(LUNAR_SHATTERED_REGOLITH))
            .item().build()
            .register();

    public static final BlockEntry<SlabBlock> LUNAR_SHATTERED_REGOLITH_SLAB = REGISTRATE.block("lunar_shattered_regolith_slab", SlabBlock::new)
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(slab(LUNAR_SHATTERED_REGOLITH))
            .item().build()
            .register();

    public static final BlockEntry<ColoredFallingBlock> LUNAR_LOOSE_REGOLITH = REGISTRATE.block("lunar_loose_regolith", p -> new ColoredFallingBlock(new ColorRGBA(-8356741), p))
            .initialProperties(() -> Blocks.GRAVEL)
            .properties(p -> p.mapColor(MapColor.STONE))
            .tag(GENERIC_CARVABLE.tag)
            .transform(pickaxeOnly())
            .item().build()
            .register();

    public static final BlockEntry<Block> LUNAR_REGOLITH_BRICK = REGISTRATE.block("lunar_regolith_brick", Block::new)
            .initialProperties(() -> Blocks.STONE)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .recipe((ctx, prov) -> prov.stonecutting(DataIngredient.items((ItemLike) LUNAR_REGOLITH), RecipeCategory.BUILDING_BLOCKS, ctx::getEntry))
            .item().build()
            .register();

    public static final BlockEntry<StairBlock> LUNAR_REGOLITH_BRICK_STAIRS = REGISTRATE.block("lunar_regolith_brick_stairs", p -> new StairBlock(LUNAR_SHATTERED_REGOLITH.getDefaultState(), p))
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(stairs(LUNAR_REGOLITH_BRICK))
            .item().build()
            .register();

    public static final BlockEntry<SlabBlock> LUNAR_REGOLITH_BRICK_SLAB = REGISTRATE.block("lunar_regolith_brick_slab", SlabBlock::new)
            .initialProperties(() -> Blocks.NETHERRACK)
            .properties(p -> p.mapColor(MapColor.STONE))
            .transform(pickaxeOnly())
            .transform(slab(LUNAR_REGOLITH_BRICK))
            .item().build()
            .register();

    public static final BlockEntry<RotatedPillarBlock> LUNAR_AGED_BASALT = REGISTRATE.block("lunar_aged_basalt", RotatedPillarBlock::new)
            .initialProperties(() -> Blocks.BASALT)
            .transform(pickaxeOnly())
            .tag(GENERIC_CARVABLE.tag)
            .blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry()))
            .item().build()
            .register();

    public static final BlockEntry<MossBlock> LUNAR_MOSS_STIFF = REGISTRATE.block("lunar_moss_stiff", MossBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    public static final BlockEntry<MossBlock> LUNAR_MOSS_SHORT = REGISTRATE.block("lunar_moss_short", MossBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    public static final BlockEntry<MossBlock> LUNAR_MOSS_SCRAGGLY = REGISTRATE.block("lunar_moss_scraggly", MossBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    public static final BlockEntry<RockBlock> LUNAR_ROCK_TALL = REGISTRATE.block("lunar_rock_tall", RockBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    public static final BlockEntry<RockBlock> LUNAR_ROCK_SMOOTH = REGISTRATE.block("lunar_rock_smooth", RockBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    public static final BlockEntry<RockBlock> LUNAR_ROCK_SPIKY = REGISTRATE.block("lunar_rock_spiky", RockBlock::new)
            .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL)
            .transform(pickaxeOnly())
            .transform(crossModelAndFlatItem()).build()
            .register();

    // MOON END

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

    private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> existingDirectionalModel(String name) {
        return b -> b.blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(), prov.models().getExistingFile(RocketNautics.path("block/" + name))));
    }

    private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> existingSimpleModel(String name) {
        return b -> b.blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(), prov.models().getExistingFile(RocketNautics.path("block/" + name))));
    }

    private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, ItemBuilder<BlockItem, BlockBuilder<T, CreateRegistrate>>> crossModelAndFlatItem() {
        return b -> b.blockstate((ctx, prov) -> {
            if (ctx.getEntry() instanceof DirectionalBlock) {
                prov.directionalBlock(ctx.getEntry(), prov.models().cross(ctx.getName(), prov.blockTexture(ctx.getEntry())).renderType("cutout"));
            } else {
                prov.simpleBlock(ctx.getEntry(), prov.models().cross(ctx.getName(), prov.blockTexture(ctx.getEntry())).renderType("cutout"));
            }
        })
                .item().model((ctx, prov) -> prov.generated(ctx::getEntry, RocketNautics.path("block/" + ctx.getName())));
    }

    private static <T extends StairBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> stairs(BlockEntry<? extends Block> parent) {
        return stairs(parent, "", "");
    }

    private static <T extends StairBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> stairs(BlockEntry<? extends Block> parent, String topSuffix, String sideSuffix) {
        return b -> b
                .recipe((ctx, prov) -> prov.stairs(DataIngredient.items((ItemLike) parent), RecipeCategory.BUILDING_BLOCKS, ctx::getEntry, ctx.getName(), true))
                .blockstate((ctx, prov) -> prov.stairsBlock(ctx.getEntry(), RocketNautics.path("block/" + parent.getKey().location().getPath() + sideSuffix), RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix), RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix)));
    }

    private static <T extends SlabBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> slab(BlockEntry<? extends Block> parent) {
        return slab(parent, "", "");
    }

    private static <T extends SlabBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> slab(BlockEntry<? extends Block> parent, String topSuffix, String sideSuffix) {
        return b -> b
                .recipe((ctx, prov) -> prov.stairs(DataIngredient.items((ItemLike) parent), RecipeCategory.BUILDING_BLOCKS, ctx::getEntry, ctx.getName(), true))
                .blockstate((ctx, prov) -> prov.slabBlock(ctx.getEntry(), RocketNautics.path("block/" + parent.getKey().location().getPath()), RocketNautics.path("block/" + parent.getKey().location().getPath() + sideSuffix), RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix), RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix)));
    }

}
