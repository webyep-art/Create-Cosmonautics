package dev.devce.rocketnautics.client;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;

import java.util.List;

public class ClientTooltipHelper {

    public static void appendTooltip(ItemStack stack, ItemLike item, List<Component> tooltip, TooltipFlag flag) {
        String baseKey = item.asItem().getDescriptionId();
        
        String funnyKey = baseKey + ".funny";
        String summaryKey = baseKey + ".summary";
        
        boolean hasFunny = I18n.exists(funnyKey);
        boolean hasSummary = I18n.exists(summaryKey);

        if (!hasFunny && !hasSummary) {
            return;
        }

        boolean shift = Screen.hasShiftDown();

        tooltip.add(Component.literal("Hold [").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Shift").withStyle(shift ? ChatFormatting.WHITE : ChatFormatting.GRAY))
            .append(Component.literal("] for Summary").withStyle(ChatFormatting.DARK_GRAY)));

        if (shift) {
            if (hasFunny) {
                tooltip.add(Component.empty());
                tooltip.addAll(FontHelper.cutStringTextComponent(I18n.get(funnyKey), FontHelper.Palette.STANDARD_CREATE));
            }
            
            if (hasSummary) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("rocketnautics.tooltip.summary_title").withStyle(ChatFormatting.GRAY));
                tooltip.addAll(FontHelper.cutStringTextComponent(I18n.get(summaryKey), FontHelper.Palette.STANDARD_CREATE.primary(), FontHelper.Palette.STANDARD_CREATE.highlight(), 1));
            }
        }
    }
}
