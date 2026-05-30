package dev.devce.rocketnautics.registry;

import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import dev.devce.rocketnautics.RocketNautics;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.Locale;
import java.util.Map;

public class RocketTags {
    public enum FluidTags {
        ROCKET_FUEL;

        public final TagKey<Fluid> tag;

        FluidTags() {
            this(NameSpace.MOD, null);
        }

        FluidTags(NameSpace namespace) {
            this(namespace, null);
        }

        FluidTags(String pathOverride) {
            this(NameSpace.MOD, pathOverride);
        }

        FluidTags(NameSpace namespace, String pathOverride) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, pathOverride == null ? name().toLowerCase(Locale.ROOT) : pathOverride);
            tag = net.minecraft.tags.FluidTags.create(id);
        }
    }

    public enum ItemTags {
        NOZZLES,
        PLATES(NameSpace.COMMON);

        public final TagKey<Item> tag;

        ItemTags() {
            this(NameSpace.MOD, null);
        }

        ItemTags(NameSpace namespace) {
            this(namespace, null);
        }

        ItemTags(String pathOverride) {
            this(NameSpace.MOD, pathOverride);
        }

        ItemTags(NameSpace namespace, String pathOverride) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, pathOverride == null ? name().toLowerCase(Locale.ROOT) : pathOverride);
            tag = net.minecraft.tags.ItemTags.create(id);
        }

        public TagKey<Item> tag() {
            return tag;
        }
    }

    public enum MetalTags {
        TITANIUM,
        TITANIUM_ALLOY(false);

        public final String name;

        public final TagKey<Item> ingots;
        public final ItemLikeTag storageBlocks;
        public final TagKey<Item> nuggets;
        public final TagKey<Item> plates;

        public final boolean hasOre;
        public final ItemLikeTag ores;
        public final TagKey<Item> rawOres;
        public final ItemLikeTag rawStorageBlocks;

        MetalTags() {
            this(true);
        }

        MetalTags(boolean hasOre) {
            this.name = Lang.asId(name());

            this.ingots = itemTag("ingots/" + this.name);
            this.storageBlocks = new ItemLikeTag("storage_blocks/" + this.name);
            this.nuggets = itemTag("nuggets/" + this.name);
            this.plates = itemTag("plates/" + this.name);

            this.hasOre = hasOre;
            this.ores = new ItemLikeTag("ores/" + this.name);
            this.rawOres = itemTag("raw_materials/" + this.name);
            this.rawStorageBlocks = new ItemLikeTag("storage_blocks/raw_" + this.name);
        }

        private static TagKey<Item> itemTag(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }

        private static TagKey<Block> blockTag(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        }

        public record ItemLikeTag(TagKey<Item> items, TagKey<Block> blocks) {
            private ItemLikeTag(String path) {
                this(itemTag(path), blockTag(path));
            }
        }

        public TagKey<Item> ingots() {
            return ingots;
        }

        public TagKey<Item> nuggets() {
            return nuggets;
        }

        public TagKey<Item> plates() {
            return plates;
        }

        public TagKey<Item> rawOres() {
            return rawOres;
        }
    }

    public enum BlockTags {
        THRUSTERS,
        RILLE_CARVABLE,
        CRATER_CARVABLE,
        GENERIC_CARVABLE;

        public final TagKey<Block> tag;

        BlockTags() {
            this(NameSpace.MOD, null);
        }

        BlockTags(NameSpace namespace) {
            this(namespace, null);
        }

        BlockTags(String pathOverride) {
            this(NameSpace.MOD, pathOverride);
        }

        BlockTags(NameSpace namespace, String pathOverride) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, pathOverride == null ? name().toLowerCase(Locale.ROOT) : pathOverride);
            tag = net.minecraft.tags.BlockTags.create(id);
        }
    }

    public enum BiomeTags {
        LUNAR_MARIA, LUNAR_HIGHLANDS, LUNAR_CHASM;

        public final TagKey<Biome> tag;

        BiomeTags() {
            this(NameSpace.MOD, null);
        }

        BiomeTags(NameSpace namespace) {
            this(namespace, null);
        }

        BiomeTags(String pathOverride) {
            this(NameSpace.MOD, pathOverride);
        }

        BiomeTags(NameSpace namespace, String pathOverride) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, pathOverride == null ? name().toLowerCase(Locale.ROOT) : pathOverride);
            tag = TagKey.create(Registries.BIOME, id);
        }
    }

    public static <T extends Block, P> NonNullFunction<BlockBuilder<T, P>, ItemBuilder<BlockItem, BlockBuilder<T, P>>> tagBlockAndItem(
            MetalTags.ItemLikeTag tag) {
        return TagGen.tagBlockAndItem(Map.of(tag.blocks(), tag.items()));
    }

    private enum NameSpace { // this exists to differentiate between the namespace override and path override constructors

        MOD(RocketNautics.MODID),
        COMMON("c");

        public final String id;

        NameSpace(String id) {
            this.id = id;
        }
    }
}
