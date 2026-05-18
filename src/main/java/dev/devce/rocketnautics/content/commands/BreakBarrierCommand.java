package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.devce.rocketnautics.RocketConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BreakBarrierCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("rn")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("break")
                .then(Commands.literal("barrier")
                    .executes(context -> {
                        boolean currentState = RocketConfig.SERVER.brokenBarrier.get();
                        boolean newState = !currentState;
                        RocketConfig.SERVER.brokenBarrier.set(newState);
                        RocketConfig.SERVER_SPEC.save();
                        
                        if (newState) {
                            context.getSource().sendSuccess(() -> Component.literal("§c[!] Rocket Thrust Barrier has been BROKEN. Limit increased to 5000N.§r"), true);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("§a[!] Rocket Thrust Barrier has been restored. Limit reset to 1000N.§r"), true);
                        }
                        return 1;
                    })
                )
            );
        dispatcher.register(builder);
    }
}
