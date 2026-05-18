package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.devce.rocketnautics.content.physics.JetpackHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class JetpackCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("jetpack")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    JetpackHandler.toggle(player);
                    boolean active = JetpackHandler.isActive(player);
                    context.getSource().sendSuccess(() -> Component.literal("Jetpack " + (active ? "enabled" : "disabled")), true);
                    return 1;
                })
            )
        );
    }
}
