package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.devce.rocketnautics.content.physics.AsteroidSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class AsteroidCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("asteroid")
                .then(Commands.literal("spawn")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ServerLevel level = player.serverLevel();
                        AsteroidSpawner.spawnAsteroid(player, level);
                        context.getSource().sendSuccess(() -> Component.literal("Forced asteroid spawn near " + player.getName().getString()), true);
                        return 1;
                    })
                )
                .then(Commands.literal("clear")
                    .executes(context -> {
                        ServerLevel level = context.getSource().getLevel();
                        AsteroidSpawner.clearAsteroids(level);
                        context.getSource().sendSuccess(() -> Component.literal("Cleared all managed asteroids"), true);
                        return 1;
                    })
                )
            )
        );
    }
}
