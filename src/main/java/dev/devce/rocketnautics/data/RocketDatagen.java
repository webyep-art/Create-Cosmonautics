package dev.devce.rocketnautics.data;

import dev.devce.rocketnautics.data.recipe.RocketMechanicalCraftingRecipeGen;
import dev.devce.rocketnautics.data.recipe.RocketMixingRecipeGen;
import dev.devce.rocketnautics.data.recipe.RocketPressingRecipeGen;
import dev.devce.rocketnautics.data.recipe.RocketStandardRecipeGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

public class RocketDatagen {

    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput output = gen.getPackOutput();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        event.addProvider(new RocketStandardRecipeGen(output, registries));
        event.addProvider(new RocketMechanicalCraftingRecipeGen(output, registries));
        event.addProvider(new RocketPressingRecipeGen(output, registries));
        event.addProvider(new RocketMixingRecipeGen(output, registries));
    }
}
