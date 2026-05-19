package dev.devce.rocketnautics.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

public class RocketTooltipHelper {

    public static void appendTooltip(ItemStack stack, ItemLike item, List<Component> tooltip, TooltipFlag flag) {
        if (FMLEnvironment.dist.isClient()) {
            dev.devce.rocketnautics.client.ClientTooltipHelper.appendTooltip(stack, item, tooltip, flag);
        }
    }
}
