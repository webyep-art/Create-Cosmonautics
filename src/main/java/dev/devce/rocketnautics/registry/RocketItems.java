package dev.devce.rocketnautics.registry;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.items.*;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import dev.simulated_team.simulated.service.SimTabService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.NonNull;


public class RocketItems {
    private static final SimulatedRegistrate REGISTRATE = RocketNautics.getRegistrate();

    public static final ItemEntry<RocketItem> MUSIC_DISC_SPACE = REGISTRATE.item("music_disc_space", RocketItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.RARE)
                    .jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "brittle_rille"))))
            .tag(Tags.Items.MUSIC_DISCS)
            .register();

    public static final ItemEntry<RocketItem> PARACHUTE_CAPSULE = REGISTRATE.item("parachute_capsule", RocketItem::new)
            .register();

    public static final ItemEntry<CreditsBookItem> CREDITS_BOOK = REGISTRATE.item("credits_book", CreditsBookItem::new)
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<JetpackItem> JETPACK = REGISTRATE.item("jetpack", JetpackItem::new)
            .properties(p -> p.stacksTo(1).fireResistant())
            .transform(noGeneratedModel())
            .tag(ItemTags.CHEST_ARMOR)
            .tag(AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.tag)
            .register();

    public static final ItemEntry<LegThrustersItem> COPPER_LEG_THRUSTERS = REGISTRATE.item("copper_leg_thrusters", p -> new LegThrustersItem(AllArmorMaterials.COPPER, p, RocketNautics.path("copper_diving")))
            .properties(p -> p.durability(ArmorItem.Type.LEGGINGS.getDurability(7)))
            .tag(ItemTags.LEG_ARMOR)
            .register();

    public static final ItemEntry<AnchorBootsItem> COPPER_ANCHOR_BOOTS = REGISTRATE.item("copper_anchor_boots", p -> new AnchorBootsItem(AllArmorMaterials.COPPER, p, RocketNautics.path("copper_diving")))
            .properties(p -> p.durability(ArmorItem.Type.BOOTS.getDurability(7)))
            .tag(ItemTags.FOOT_ARMOR)
            .register();

    static { REGISTRATE.setCreativeTab(RocketTabs.RESOURCE_TAB); }

    public static final ItemEntry<RocketItem> TITANIUM_INGOT = taggedIngredient("titanium_ingot", RocketTags.MetalTags.TITANIUM.ingots, Tags.Items.INGOTS);

    public static final ItemEntry<RocketItem> RAW_TITANIUM = taggedIngredient("raw_titanium", RocketTags.MetalTags.TITANIUM.rawOres, Tags.Items.RAW_MATERIALS);

    public static final ItemEntry<RocketItem> CRUSHED_TITANIUM = taggedIngredient("crushed_raw_titanium", AllTags.AllItemTags.CRUSHED_RAW_MATERIALS.tag);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY = taggedIngredientFireResistant("titanium_alloy", RocketTags.MetalTags.TITANIUM_ALLOY.ingots, Tags.Items.INGOTS);

    public static final ItemEntry<RocketItem> TITANIUM_NUGGET = taggedIngredient("titanium_nugget", RocketTags.MetalTags.TITANIUM.nuggets, Tags.Items.NUGGETS);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY_NUGGET = taggedIngredientFireResistant("titanium_alloy_nugget", RocketTags.MetalTags.TITANIUM_ALLOY.nuggets, Tags.Items.NUGGETS);

    public static final ItemEntry<RocketItem> TITANIUM_SHEET = taggedIngredient("titanium_sheet", RocketTags.MetalTags.TITANIUM.plates, RocketTags.ItemTags.PLATES.tag);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY_SHEET = taggedIngredientFireResistant("titanium_alloy_sheet", RocketTags.MetalTags.TITANIUM_ALLOY.plates, RocketTags.ItemTags.PLATES.tag);

    public static final ItemEntry<RocketItem> TITANIUM_NOZZLE = taggedIngredientFireResistant("titanium_nozzle", RocketTags.ItemTags.NOZZLES.tag);

    public static final ItemEntry<RocketItem> COPPER_NOZZLE = taggedIngredient("copper_nozzle", RocketTags.ItemTags.NOZZLES.tag);

    static { REGISTRATE.setCreativeTab(null); }

    @SafeVarargs
    private static ItemEntry<RocketItem> taggedIngredient(String name, TagKey<Item>... tags) {
        return REGISTRATE.item(name, RocketItem::new)
                .tag(tags)
                .register();
    }

    @SafeVarargs
    private static ItemEntry<RocketItem> taggedIngredientFireResistant(String name, TagKey<Item>... tags) {
        return REGISTRATE.item(name, RocketItem::new)
                .tag(tags)
                .properties(Item.Properties::fireResistant)
                .register();
    }

    public static void init() {}

    static @NonNull <T extends Item, V> NonNullFunction<ItemBuilder<T, V>, ItemBuilder<T, V>> noGeneratedModel() {
        return i -> i.model((ctx, prov) -> {});
    }
}
