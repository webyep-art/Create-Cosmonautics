package dev.devce.rocketnautics.data;

import com.tterrag.registrate.providers.RegistrateTagsProvider;
import dev.devce.rocketnautics.registry.RocketTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class RocketBlockTagsProvider {

    protected static void addTags(RegistrateTagsProvider.IntrinsicImpl<Block> prov) {
        prov.addTag(RocketTags.BlockTags.GENERIC_CARVABLE.tag)
                .add(Blocks.BASALT);
        prov.addTag(RocketTags.BlockTags.RILLE_CARVABLE.tag).addTag(RocketTags.BlockTags.GENERIC_CARVABLE.tag);
        prov.addTag(RocketTags.BlockTags.CRATER_CARVABLE.tag).addTag(RocketTags.BlockTags.GENERIC_CARVABLE.tag);
    }
}
