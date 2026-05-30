package dev.devce.rocketnautics;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.devce.rocketnautics.compat.computercraft.ComputerCraftCompat;
import dev.devce.rocketnautics.content.commands.GravityCommand;
import dev.devce.rocketnautics.content.commands.JetpackCommand;
import dev.devce.rocketnautics.content.commands.ShipCopyPasteCommand;
import dev.devce.rocketnautics.content.commands.TimescaleCommand;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.devce.rocketnautics.data.RocketDatagen;
import dev.devce.rocketnautics.network.NetworkHandler;
import dev.devce.rocketnautics.registry.*;
import dev.devce.websnodelib.internal.InternalNodes;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/**
 * Main class for the Cosmonautics (RocketNautics) mod.
 * This class handles mod initialization, configuration registration,
 * and various system setups for physics, commands, and registry.
 */
@Mod(RocketNautics.MODID)
public class RocketNautics {
    /** The unique identifier for this mod. */
    public static final String MODID = "rocketnautics";
    /** Global logger instance for this mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final NonNullSupplier<RocketRegistrate> REGISTRATE = NonNullSupplier.lazy(() ->
            (RocketRegistrate) new RocketRegistrate(path(MODID), MODID).defaultCreativeTab((ResourceKey<CreativeModeTab>) null));
    /**
     * Constructor for the mod. Performs initial registration of configs, blocks, and handlers.
     *
     * @param modEventBus The event bus for mod-specific events.
     * @param modContainer The container for this mod instance.
     */
    public RocketNautics(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        LOGGER.info("Initializing Cosmonautics!");

        // Register mod configurations
        modContainer.registerConfig(ModConfig.Type.SERVER, (net.neoforged.fml.config.IConfigSpec) RocketConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, (net.neoforged.fml.config.IConfigSpec) RocketConfig.CLIENT_SPEC);

        RocketDensityFunctions.register(modEventBus);
        RocketFloatProviders.register(modEventBus);
        RocketWorldCarvers.register(modEventBus);
        RocketFeatures.register(modEventBus);

        modEventBus.addListener(RocketDatagen::gatherData);

        // Register registries
        getRegistrate().registerEventListeners(modEventBus);
        setTooltips();

        RocketItems.init();
        RocketBlocks.init();
        RocketTabs.register(modEventBus);
        RocketBlockEntities.register(modEventBus);
        RocketParticles.register(modEventBus);
        RocketSounds.register(modEventBus);
        InternalNodes.register();
        dev.devce.rocketnautics.registry.RocketNodes.register();

        RocketDataComponents.register(modEventBus);

        // Register mod-bus event subscribers manually to avoid deprecated bus() parameter
        modEventBus.register(NetworkHandler.class);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            modEventBus.register(dev.devce.rocketnautics.client.ClientModEvents.class);
            modEventBus.register(RocketNauticsClient.class);
            NeoForge.EVENT_BUS.register(dev.devce.rocketnautics.client.RocketNauticsClientEvents.class);
        }

        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);

        // Initialize physics and game mechanics handlers
        GlobalSpacePhysicsHandler.init();
        dev.devce.rocketnautics.content.physics.AsteroidSpawner.init();
        dev.devce.rocketnautics.content.physics.SpaceTransitionHandler.init();

        if (ModList.get().isLoaded("computercraft"))
            ComputerCraftCompat.init();
    }

    public static ResourceLocation path(final String path) {
        return ResourceLocation.tryBuild(MODID, path);
    }

    public static RocketRegistrate getRegistrate() {
        return REGISTRATE.get();
    }

    public static void setTooltips() {
        getRegistrate().setTooltipModifierFactory(item -> {
            final Rarity rarity = item.getDefaultInstance().getRarity();
            FontHelper.Palette color = FontHelper.Palette.STANDARD_CREATE;
            if (rarity == Rarity.EPIC)
                color = new FontHelper.Palette(TooltipHelper.styleFromColor(SimColors.EPIC_OURPLE), TooltipHelper.styleFromColor(rarity.color()));

            return new ItemDescription
                    .Modifier(item, color)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item)));
        });
    }

    /**
     * Common setup logic that runs during the mod loading phase.
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Cosmonautics Setup");
        // Sable altitude limits override has been moved to onServerAboutToStart event to prevent early-access exceptions
    }

    /**
     * Registers console commands for the mod.
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GravityCommand.register(event.getDispatcher());
        ShipCopyPasteCommand.register(event.getDispatcher());
        JetpackCommand.register(event.getDispatcher());
        dev.devce.rocketnautics.content.commands.AsteroidCommand.register(event.getDispatcher());
        dev.devce.rocketnautics.content.commands.BreakBarrierCommand.register(event.getDispatcher());
        TimescaleCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        try {
            dev.ryanhcode.sable.SableConfig.SUB_LEVEL_REMOVE_MIN.set(-1000000.0);
            dev.ryanhcode.sable.SableConfig.SUB_LEVEL_REMOVE_MAX.set(1000000.0);
            LOGGER.info("Successfully expanded Sable altitude limits in ServerAboutToStartEvent.");
        } catch (Exception e) {
            LOGGER.error("Failed to override SableConfig in ServerAboutToStartEvent: {}", e.getMessage());
        }
    }

    /**
     * Temporary fix for <a href="https://github.com/ryanhcode/sable/issues/919">Sable issue #919</a>
     * that corrupts the world when logging out while seated on a sublevel outside the overworld.
     */
    @SubscribeEvent
    public void preventWorldCorruptionOnSeatedLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isPassenger()) return;
        if (player.level().dimension() != Level.OVERWORLD) {
            player.stopRiding();
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
    }
}
