package dev.devce.rocketnautics.registry;

import com.tterrag.registrate.util.entry.ItemEntry;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.items.CreditsBookItem;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.items.RocketItem;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.Tags;


public class RocketItems {
    private static final SimulatedRegistrate REGISTRATE = RocketNautics.getRegistrate();

    public static final ItemEntry<RocketItem> MUSIC_DISC_SPACE = REGISTRATE.item("music_disc_space", RocketItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.RARE)
                    .jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "brittle_rille"))))
            .tag(Tags.Items.MUSIC_DISCS)
            .register();

    public static final ItemEntry<CreditsBookItem> CREDITS_BOOK = REGISTRATE.item("credits_book", CreditsBookItem::new)
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> {})
            .register();

    public static final ItemEntry<JetpackItem> JETPACK = REGISTRATE.item("jetpack", JetpackItem::new)
            .properties(p -> p.stacksTo(1).fireResistant())
            .register();

    static { REGISTRATE.setCreativeTab(RocketTabs.RESOURCE_TAB); }

    public static final ItemEntry<RocketItem> TITANIUM_INGOT = taggedIngredient("titanium_ingot", RocketTags.MetalTags.TITANIUM.ingots, Tags.Items.INGOTS);

    public static final ItemEntry<RocketItem> RAW_TITANIUM = taggedIngredient("raw_titanium", RocketTags.MetalTags.TITANIUM.rawOres, Tags.Items.RAW_MATERIALS);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY = taggedIngredientFireResistant("titanium_alloy", RocketTags.MetalTags.TITANIUM_ALLOY.ingots, Tags.Items.INGOTS);

    public static final ItemEntry<RocketItem> TITANIUM_NUGGET = taggedIngredient("titanium_nugget", RocketTags.MetalTags.TITANIUM.nuggets, Tags.Items.NUGGETS);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY_NUGGET = taggedIngredientFireResistant("titanium_alloy_nugget", RocketTags.MetalTags.TITANIUM_ALLOY.nuggets, Tags.Items.NUGGETS);

    public static final ItemEntry<RocketItem> TITANIUM_SHEET = taggedIngredient("titanium_sheet", RocketTags.MetalTags.TITANIUM.plates, RocketTags.ItemTags.PLATES.tag);

    public static final ItemEntry<RocketItem> TITANIUM_ALLOY_SHEET = taggedIngredientFireResistant("titanium_alloy_sheet", RocketTags.MetalTags.TITANIUM_ALLOY.plates, RocketTags.ItemTags.PLATES.tag);

    public static final ItemEntry<RocketItem> TITANIUM_NOZZLE = taggedIngredientFireResistant("titanium_nozzle", RocketTags.ItemTags.NOZZLES.tag);

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
}
