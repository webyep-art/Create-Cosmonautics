package dev.devce.rocketnautics.client;

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

    private static final byte FALLBACK;

    // TODO add more colors for sun and moon
    public static final byte SUN_1;
    public static final byte MOON_1;

    static {
        registerColor(0);
        // overworld
        byte ocean = setBiomeColor(BiomeTags.IS_OCEAN, 10, 40 ,120);
        associateBiomeTag(BiomeTags.IS_DEEP_OCEAN, ocean);
        setBiomeColor(BiomeTags.IS_RIVER, 20, 80, 180);
        setBiomeColor(BiomeTags.IS_BEACH, 210, 190, 140);
        setBiomeColor(BiomeTags.HAS_VILLAGE_DESERT, 200, 180, 100);
        setBiomeColor(BiomeTags.IS_FOREST, 20, 90, 30);
        setBiomeColor(BiomeTags.IS_JUNGLE, 10, 70, 20);
        setBiomeColor(BiomeTags.IS_TAIGA, 20, 60, 40);
        setBiomeColor(BiomeTags.IS_SAVANNA, 160, 140, 70);
        setBiomeColor(Tags.Biomes.IS_SNOWY, 220, 220, 230);
        setBiomeColor(BiomeTags.IS_BADLANDS, 180, 80, 30);
        setBiomeColor(Tags.Biomes.IS_SWAMP, 50, 70, 40);
        setBiomeColor(Tags.Biomes.IS_WINDSWEPT, 80, 100, 80);
        setBiomeColor(Tags.Biomes.IS_MUSHROOM, 100, 90, 100);
        byte stony = setBiomeColor(BiomeTags.IS_MOUNTAIN, 120, 120, 120);
        associateBiomeTag(Tags.Biomes.IS_STONY_SHORES, stony);
        FALLBACK = setBiomeColor(Tags.Biomes.IS_PLAINS, 30, 120, 40);
        // sun
        SUN_1 = addColor(250, 230, 90);
        // moon
        MOON_1 = addColor(160, 160, 160);
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
        return setBiomeColor(biome, (r << 24) | (b << 16) | (g << 8) | r);
    }

    public static byte setBiomeColor(TagKey<Biome> biome, int packedColor) {
        byte id = registerColor(packedColor);
        associateBiomeTag(biome, id);
        return id;
    }


    public static int getPackedColor(byte id) {
        return idToColor.getInt(id);
    }

    public static byte getBiomeColor(Holder<Biome> biome) {
        for (TagKey<Biome> key : biomeToId.keySet()) {
            if (biome.is(key)) {
                return biomeToId.getByte(key);
            }
        }
        return FALLBACK;
    }
}
