package dev.devce.rocketnautics.content.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/**
 * A custom item class that provides a pre-written credits book for the mod.
 * Displays the team members, contributors, and hall of fame entries.
 */
public class CreditsBookItem extends Item {

    public CreditsBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return buildCreditsBook();
    }

    /**
     * Constructs a pre-formatted written book stack with the mod's credits.
     * 
     * @return An ItemStack of a written book containing Cosmonautics credits.
     */
    public static ItemStack buildCreditsBook() {
        ItemStack credits = new ItemStack(Items.WRITTEN_BOOK);

        credits.set(DataComponents.ITEM_NAME,
            Component.translatable("item.rocketnautics.credits_book")
                .withStyle(ChatFormatting.GOLD));

        credits.set(DataComponents.WRITTEN_BOOK_CONTENT,
            new WrittenBookContent(
                Filterable.passThrough("Credits"),
                "webyep",
                0,
                List.of(
                    Filterable.passThrough(
                        Component.literal("")
                            .append(Component.literal("   Cosmonautics\n\n")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append(Component.literal("This book is dedicated\nto everyone who helped\nmake this project\npossible!\n\n")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                            .append(Component.literal("Thank you all!")
                                .withStyle(ChatFormatting.DARK_RED))
                    ),
                    Filterable.passThrough(
                        Component.literal("")
                            .append(Component.literal("      Credits\n\n")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append(Component.literal("Maintainer: ").withStyle(ChatFormatting.DARK_BLUE))
                            .append(Component.literal("web\n"))
                            .append(Component.literal("Contributor: ").withStyle(ChatFormatting.DARK_BLUE))
                            .append(Component.literal("SSnowly, M_W_K, snusxd, Sk4sBrawl\n"))
                            .append(Component.literal("Artist: ").withStyle(ChatFormatting.DARK_BLUE))
                            .append(Component.literal("LordAret, Mruranium\n"))
                            .append(Component.literal("Ideas: ").withStyle(ChatFormatting.DARK_GREEN))
                            .append(Component.literal("6j, Harbinger\n"))
                            .append(Component.literal("Testers: ").withStyle(ChatFormatting.DARK_RED))
                            .append(Component.literal("ABOBA, Kernos, MrFeddy\n"))
                            .append(Component.literal("Music: ").withStyle(ChatFormatting.DARK_AQUA))
                            .append(Component.literal("Kevin Macleod\n"))
                            .append(Component.literal("Localization: ").withStyle(ChatFormatting.DARK_GREEN))
                            .append(Component.literal("PigeonNian"))
                    ),
                    Filterable.passThrough(
                        Component.literal("")
                            .append(Component.literal("    Hall of Fame\n\n")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append(Component.literal("Apollo1641\n")
                                .withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD))
                            .append(Component.literal("Winner of the First Rocket Contest!")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                    )
                ),
                true
            )
        );

        return credits;
    }
}
