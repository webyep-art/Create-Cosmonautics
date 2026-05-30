package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.registry.RocketTags;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

public final class PlanetColors {
    public static final int ARRAY_SIZE = 65536; // 256 * 256
    public static final byte[] BLANK = new byte[ARRAY_SIZE];

    private static final IntList idToColor = new IntArrayList();
    private static final Object2ByteLinkedOpenHashMap<TagKey<Biome>> biomeToId = new Object2ByteLinkedOpenHashMap<>();

    public static final byte OCEAN;
    public static final byte RIVER;

    public static final byte FALLBACK;

    // TODO add more colors for sun and moon
    public static final byte SUN_1;

    public static final byte MARS_RED;
    public static final byte MARS_DARK_RED;
    public static final byte MARS_ICE;

    public static final byte GAS_ORANGE;
    public static final byte GAS_BROWN;
    public static final byte GAS_YELLOW;
    public static final byte GAS_RED;

    public static final byte ICE_BLUE;
    public static final byte ICE_CYAN;
    public static final byte ICE_DARK_BLUE;

    static {
        registerColor(0);
        // overworld
        OCEAN = setBiomeColor(BiomeTags.IS_OCEAN, 15, 45 ,135); // Curated deep royal blue
        associateBiomeTag(BiomeTags.IS_DEEP_OCEAN, OCEAN);
        RIVER = setBiomeColor(BiomeTags.IS_RIVER, 25, 95, 215); // Vibrant blue
        setBiomeColor(BiomeTags.IS_BEACH, 225, 205, 155); // Warm sand
        setBiomeColor(BiomeTags.HAS_VILLAGE_DESERT, 215, 195, 115); // Golden sand
        setBiomeColor(Tags.Biomes.IS_PLAINS, 45, 145, 55); // Emerald green
        setBiomeColor(BiomeTags.IS_FOREST, 25, 105, 35); // Lush dark forest green
        setBiomeColor(BiomeTags.IS_JUNGLE, 15, 85, 25); // Deep jungle teal-green
        setBiomeColor(BiomeTags.IS_TAIGA, 30, 75, 55); // Cool pine green
        setBiomeColor(BiomeTags.IS_SAVANNA, 160, 140, 70);
        setBiomeColor(Tags.Biomes.IS_SNOWY, 240, 240, 245); // Pristine snow white
        setBiomeColor(BiomeTags.IS_BADLANDS, 195, 90, 40); // Terracotta orange
        setBiomeColor(Tags.Biomes.IS_SWAMP, 50, 70, 40);
        setBiomeColor(Tags.Biomes.IS_WINDSWEPT, 80, 100, 80);
        setBiomeColor(Tags.Biomes.IS_MUSHROOM, 100, 90, 100);
        byte stony = setBiomeColor(BiomeTags.IS_MOUNTAIN, 135, 135, 135); // Slate grey
        associateBiomeTag(Tags.Biomes.IS_STONY_SHORES, stony);
        FALLBACK = addColor(30, 120 ,40);
        // TODO separate these into "color palettes" that are associated with the cube planet
        // send the color palette definition with the render data to 'interpret' the byte array
        // sun
        SUN_1 = addColor(250, 230, 90);
        // moon
        setBiomeColor(RocketTags.BiomeTags.LUNAR_CHASM.tag, 220, 150, 70);
        setBiomeColor(RocketTags.BiomeTags.LUNAR_HIGHLANDS.tag, 190, 190, 190);
        setBiomeColor(RocketTags.BiomeTags.LUNAR_MARIA.tag, 90, 90, 90);

        // mars
        MARS_RED = addColor(185, 80, 45);
        MARS_DARK_RED = addColor(125, 45, 25);
        MARS_ICE = addColor(245, 240, 240);

        // gas giant
        GAS_ORANGE = addColor(215, 120, 45);
        GAS_BROWN = addColor(140, 75, 30);
        GAS_YELLOW = addColor(235, 185, 90);
        GAS_RED = addColor(175, 40, 30);

        // ice world
        ICE_BLUE = addColor(35, 75, 195);
        ICE_CYAN = addColor(95, 185, 235);
        ICE_DARK_BLUE = addColor(15, 35, 115);
    }

    public static byte addColor(int r, int g, int b) {
        return registerColor((255 << 24) | (b << 16) | (g << 8) | r);
    }

    public static byte addColor(int r, int g, int b, int a) {
        return registerColor((r << 24) | (b << 16) | (g << 8) | r);
    }

    public static byte registerColor(int packedColor) {
        int index = idToColor.indexOf(packedColor);
        if (index != -1) {
            return (byte) index;
        }
        idToColor.add(packedColor);
        return (byte) (idToColor.size() - 1);
    }

    public static void associateBiomeTag(TagKey<Biome> biome, byte colorID) {
        biomeToId.putAndMoveToLast(biome, colorID);
    }

    public static byte setBiomeColor(TagKey<Biome> biome, int r, int g, int b) {
        return setBiomeColor(biome, (255 << 24) | (b << 16) | (g << 8) | r);
    }

    public static byte setBiomeColor(TagKey<Biome> biome, int r, int g, int b, int a) {
        return setBiomeColor(biome, (a << 24) | (b << 16) | (g << 8) | r);
    }

    public static byte setBiomeColor(TagKey<Biome> biome, int packedColor) {
        byte id = registerColor(packedColor);
        associateBiomeTag(biome, id);
        return id;
    }


    public static int getPackedColor(byte id) {
        return idToColor.getInt(id);
    }

    public static int[] getUnpackedColorARGB(byte id) {
        int argb = getPackedColor(id);
        int a = (argb >> 24) & 0xFF;
        int b = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int r = argb & 0xFF;
        return new int[] {a, r, g, b};
    }

    public static byte getBiomeColor(Holder<Biome> biome) {
        for (TagKey<Biome> key : biomeToId.keySet()) {
            if (biome.is(key)) {
                return biomeToId.getByte(key);
            }
        }
        return FALLBACK;
    }

    public static int getReservedCount() {
        return idToColor.size();
    }

    public static boolean isWater(byte id) {
        return id == OCEAN || id == RIVER;
    }
}
