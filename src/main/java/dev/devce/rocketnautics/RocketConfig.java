package dev.devce.rocketnautics;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for RocketNautics.
 * Uses NeoForge's ModConfigSpec to define and register server and client-side settings.
 */
public class RocketConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        final Pair<Server, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();

        final Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    /**
     * Server-side configuration settings.
     * These settings are synced from the server to all connected clients.
     */
    public static class Server {
        public final ModConfigSpec.IntValue maxFuelConsumption;
        public final ModConfigSpec.DoubleValue jetpackThrust;
        public final ModConfigSpec.DoubleValue jetpackSprintThrust;
        public final ModConfigSpec.IntValue ignitionFlow;
        public final ModConfigSpec.IntValue steamMinFlow;
        public final ModConfigSpec.IntValue entitySpeedLimit;
        public final ModConfigSpec.BooleanValue enableEngineDebugLogging;
        public final ModConfigSpec.BooleanValue brokenBarrier;

        public Server(ModConfigSpec.Builder builder) {
            builder.push("Thrusters");
            maxFuelConsumption = builder
                    .comment("Maximum fuel consumption in mB/tick")
                    .defineInRange("maxFuelConsumption", 40, 1, 1000);
            ignitionFlow = builder
                    .comment("Flow threshold for full ignition (mB/tick)")
                    .defineInRange("ignitionFlow", 5, 1, 100);
            steamMinFlow = builder
                    .comment("Flow threshold for pre-ignition steam phase (mB/tick)")
                    .defineInRange("steamMinFlow", 2, 1, 100);
            entitySpeedLimit = builder
                    .comment("Maximum speed in m/s entities can reach in space before drag kicks in")
                    .defineInRange("entitySpeedLimit", 80, 0, 320);
            enableEngineDebugLogging = builder
                    .comment("Enable debug logging for engine fuel and thrust (can cause spam)")
                    .define("enableEngineDebugLogging", false);
            brokenBarrier = builder
                    .comment("Allow engine thrust to exceed standard limits (up to 5000N)")
                    .define("brokenBarrier", false);
            builder.pop();

            builder.push("Jetpack");
            jetpackThrust = builder
                    .comment("Standard thrust power of the jetpack")
                    .defineInRange("jetpackThrust", 0.15, 0.01, 1.0);
            jetpackSprintThrust = builder
                    .comment("Thrust power of the jetpack while sprinting")
                    .defineInRange("jetpackSprintThrust", 0.35, 0.01, 2.0);
            builder.pop();
        }
    }

    /**
     * Client-side configuration settings.
     * These settings are local to each player's client.
     */
    public static class Client {
        public final ModConfigSpec.DoubleValue shakeIntensity;
        public final ModConfigSpec.DoubleValue shakeRadius;
        public final ModConfigSpec.BooleanValue enableDynamicRenderDistance;
        public final ModConfigSpec.BooleanValue showDebugOverlay;
        public final ModConfigSpec.IntValue planetRenderMaximumScale;
        public final ModConfigSpec.DoubleValue orbitPredictionStepFactor;
        public final ModConfigSpec.IntValue orbitPredictionSteps;

        public Client(ModConfigSpec.Builder builder) {
            builder.push("Visuals");
            shakeIntensity = builder
                    .comment("Intensity multiplier for camera shake near engines")
                    .defineInRange("shakeIntensity", 0.5, 0.0, 5.0);
            shakeRadius = builder
                    .comment("Radius in blocks where camera shake is felt")
                    .defineInRange("shakeRadius", 8.0, 1.0, 64.0);
            enableDynamicRenderDistance = builder
                    .comment("Enable automatic render distance adjustment based on altitude")
                    .define("enableDynamicRenderDistance", true);
            showDebugOverlay = builder
                    .comment("Show the Cosmonautics debug overlay (Alt/Speed/etc)")
                    .define("showDebugOverlay", false);
            planetRenderMaximumScale = builder
                    .comment("The maximum texture scale for the planet render.")
                    .comment("Recommended scale for the render to maintain visual structure is 15 or 16.")
                    .defineInRange("planetRenderMaximumScale", 100, SkyDataHandler.MIN_POWER_SIZE, 100);
            orbitPredictionStepFactor = builder
                    .comment("Controls the distance between each step in the hologram table's orbit prediction.")
                    .comment("A smaller number will increase visual fidelity and accuracy, but reduce prediction length.")
                    .defineInRange("orbitPredictionStepFactor", 1d, 0.1d, 10d);
            orbitPredictionSteps = builder
                    .comment("The number of steps to compute in the hologram table's orbit prediction.")
                    .comment("Set to zero to disable the prediction entirely. Too many steps can cause lag!")
                    .defineInRange("orbitPredictionSteps", 1000, 0, 10000);
            builder.pop();
        }
    }
}
