package dev.devce.rocketnautics.content.physics;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.network.ReentryHeatPayload;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Core physics handler for space-related mechanics.
 * Manages zero-gravity effects for ships and entities, reentry heat damage, 
 * and space suffocation mechanics.
 */
@EventBusSubscriber(modid = RocketNautics.MODID)
public class GlobalSpacePhysicsHandler {

    private static final double SPACE_GRAVITY_START_Y = 2000.0;
    private static final double SPACE_GRAVITY_FULL_Y = 5000.0;
    private static final double REENTRY_HEAT_START_Y = 1000.0;
    private static final double REENTRY_HEAT_END_Y = 2500.0;
    private static final double REENTRY_SPEED_THRESHOLD = 60.0;
    private static final double REENTRY_FRICTION_COEFF = 0.3;
    private static final double MAX_Q_STRESS_THRESHOLD = 625.0; // density * speed^2
    private static final double DEFAULT_CALIBRATION = 0.99895;

    private static final ResourceLocation SPACE_GRAVITY_ID = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "space_gravity");
    private static final AttributeModifier SPACE_GRAVITY_MODIFIER = new AttributeModifier(
            SPACE_GRAVITY_ID,
            -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private static Float gravityOverride = null;
    private static double calibrationMultiplier = DEFAULT_CALIBRATION;

    public static void setGravityOverride(float value) {
        gravityOverride = value;
    }

    public static void resetGravityOverride() {
        gravityOverride = null;
    }

    public static void setCalibration(double value) {
        calibrationMultiplier = value;
    }

    /**
     * Initializes the physics tick listener using Sable's physics system.
     */
    public static void init() {
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            ServerLevel level = physicsSystem.getLevel();
            SubLevelContainer container = SubLevelContainer.getContainer(level);

            if (container == null) return;

            for (SubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel instanceof ServerSubLevel serverSubLevel) {
                    RigidBodyHandle handle = physicsSystem.getPhysicsHandle(serverSubLevel);
                    if (handle != null) {
                        processSubLevelPhysics(serverSubLevel, handle, level, timeStep);
                    }
                }
            }
        });
    }

    private static void processSubLevelPhysics(ServerSubLevel subLevel, RigidBodyHandle handle, ServerLevel level, double timeStep) {
        Vector3d worldPos = subLevel.logicalPose().position();
        if (worldPos == null) return;

        applyZeroGravity(subLevel, handle, level, worldPos, timeStep);
        // applyReentryHeat(subLevel, handle, level, worldPos, timeStep);
        // applyMaxQStress(subLevel, handle, level, worldPos, timeStep);
    }

    /**
     * Applies an anti-gravity impulse to counter-act the world's gravity.
     * The impulse is calculated based on the ship's mass and the current gravity factor.
     */
    private static void applyZeroGravity(ServerSubLevel subLevel, RigidBodyHandle handle, ServerLevel level, Vector3d worldPos, double timeStep) {
        double gravityFactor = calculateGravityFactor(level, worldPos.y());
        if (gravityFactor <= 0.0) return;

        double mass = subLevel.getMassTracker().getMass();
        Vector3d gravityVector = DimensionPhysicsData.getGravity(level);

        // Calculate anti-gravity force: F = -m * g * factor
        Vector3d antiGravityImpulse = new Vector3d(gravityVector)
                .mul(-1.0 * mass * gravityFactor * timeStep * calibrationMultiplier);

        // Transform impulse to local ship coordinates before applying
        Quaterniond orientation = subLevel.logicalPose().orientation();
        Vector3d localImpulse = orientation.transformInverse(antiGravityImpulse, new Vector3d());
        handle.applyLinearImpulse(localImpulse);
    }

    /**
     * Calculates the gravity reduction factor based on altitude.
     * @return 0.0 (full gravity) to 1.0 (zero gravity).
     */
    public static double calculateGravityFactor(net.minecraft.world.level.Level level, double y) {
        if (gravityOverride != null) {
            return 1.0 - gravityOverride;
        }

        // Space dimension is always zero gravity
        if (level.dimension().location().getPath().equals("space") || DeepSpaceData.isDeepSpace(level)) {
            return 1.0;
        }

        // Overworld transition logic: starts at 2000m, full at 5000m
        if (y <= SPACE_GRAVITY_START_Y) return 0.0;
        return Math.clamp((y - SPACE_GRAVITY_START_Y) / (SPACE_GRAVITY_FULL_Y - SPACE_GRAVITY_START_Y), 0.0, 1.0);
    }

    /**
     * Applies structural damage to ships moving too fast in dense atmosphere.
     */
    private static void applyMaxQStress(ServerSubLevel subLevel, RigidBodyHandle handle, ServerLevel level, Vector3d worldPos, double timeStep) {
        double altitude = worldPos.y();
        if (altitude > 10000.0) return; // Expanded limit for testing

        double density = 1.0 - calculateGravityFactor(level, altitude);
        if (density < 0.05) return; // Too thin to cause stress

        Vector3d velocity = new Vector3d(handle.getLinearVelocity());
        double speedSq = velocity.lengthSquared();
        double stress = density * speedSq;

        if (stress > MAX_Q_STRESS_THRESHOLD) {
            double intensity = (stress - MAX_Q_STRESS_THRESHOLD) / MAX_Q_STRESS_THRESHOLD;
            
            // Random block damage - much more aggressive
            if (level.random.nextFloat() < Math.min(0.9f, intensity * 0.5f)) {
                damageRandomShipBlock(subLevel, level);
            }

            // Damage entities inside
            if (intensity > 0.5 && level.getGameTime() % 10 == 0) {
                AABB area = new AABB(worldPos.x - 5, worldPos.y - 5, worldPos.z - 5, worldPos.x + 5, worldPos.y + 5, worldPos.z + 5);
                level.getEntitiesOfClass(LivingEntity.class, area).forEach(e -> {
                    e.hurt(level.damageSources().generic(), (float)intensity * 2.0f);
                });
            }

            // Visual feedback (reuse reentry heat with higher intensity or dedicated effect)
            if (level.getGameTime() % 15 == 0) {
                for (ServerPlayer player : level.players()) {
                    PacketDistributor.sendToPlayer(player, new ReentryHeatPayload(worldPos.x, worldPos.y, worldPos.z, (float)intensity));
                }
            }
        }
    }

    private static void damageRandomShipBlock(ServerSubLevel subLevel, ServerLevel level) {
        var plot = subLevel.getPlot();
        var minChunk = plot.getChunkMin();
        var maxChunk = plot.getChunkMax();

        int minX = minChunk.x * 16;
        int maxX = maxChunk.x * 16 + 15;
        int minZ = minChunk.z * 16;
        int maxZ = maxChunk.z * 16 + 15;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Target the actual blocks in the PLOT area
        for (int i = 0; i < 40; i++) { // More attempts
            int x = minX + level.random.nextInt(maxX - minX + 1);
            int z = minZ + level.random.nextInt(maxZ - minZ + 1);
            int y = minY + level.random.nextInt(maxY - minY + 1);
            
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            
            if (!state.isAir()) {
                // Break the real block in the plot
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                
                // Add explosion at the plot location
                level.explode(null, x + 0.5, y + 0.5, z + 0.5, 2.0f, ServerLevel.ExplosionInteraction.BLOCK);
                
                // Also spawn particles at the projected world position of the ship for visual feedback
                Vector3d shipWorldPos = subLevel.logicalPose().position();
                for (ServerPlayer player : level.players()) {
                    PacketDistributor.sendToPlayer(player, new ReentryHeatPayload(shipWorldPos.x, shipWorldPos.y, shipWorldPos.z, 1.0f));
                }
                return;
            }
        }
    }

    /**
     * Applies atmospheric friction and heat damage when descending at high speeds.
     */
    private static void applyReentryHeat(ServerSubLevel subLevel, RigidBodyHandle handle, ServerLevel level, Vector3d worldPos, double timeStep) {
        double y = worldPos.y();
        // Reentry effects occur between 1000m and 2500m
        if (y > REENTRY_HEAT_END_Y || y < REENTRY_HEAT_START_Y) return;

        Vector3d velocity = new Vector3d(handle.getLinearVelocity());
        double descentSpeed = -velocity.y();

        if (descentSpeed > REENTRY_SPEED_THRESHOLD) {
            float intensity = (float) Math.clamp((descentSpeed - REENTRY_SPEED_THRESHOLD) / REENTRY_SPEED_THRESHOLD, 0.0, 1.0);
            
            // Apply upward friction force to simulate atmospheric drag
            Vector3d friction = new Vector3d(0, descentSpeed * intensity * REENTRY_FRICTION_COEFF, 0);
            handle.applyLinearImpulse(friction.mul(subLevel.getMassTracker().getMass() * timeStep));

            // Burn entities inside the ship area
            if (intensity > 0.3 && level.getGameTime() % 20 == 0) {
                AABB damageArea = new AABB(worldPos.x - 4, worldPos.y - 4, worldPos.z - 4, worldPos.x + 4, worldPos.y + 4, worldPos.z + 4);
                level.getEntitiesOfClass(LivingEntity.class, damageArea).forEach(entity -> {
                    entity.hurt(level.damageSources().onFire(), intensity * 3.0f);
                });
            }
            
            // Notify clients to render heat effects
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, new ReentryHeatPayload(worldPos.x, worldPos.y, worldPos.z, intensity));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            // applyFallingHeatDamage(living);
            applySpaceSuffocation(living);
        }
    }



    /**
     * Applies suffocation damage to entities in space or at high altitudes 
     * if they lack proper life support equipment (helmets/tanks).
     */
    private static void applySpaceSuffocation(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        // Suffocation starts above 1000m or in the space dimension
        if (entity.getY() > 1000 || entity.level().dimension().location().getPath().equals("space")) {
            if (entity instanceof net.minecraft.world.entity.player.Player player && (player.isCreative() || player.isSpectator())) return;
            
            net.minecraft.world.item.ItemStack headItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            net.minecraft.world.item.ItemStack chestItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            
            boolean hasProtection = false;
            
            // Check for items tagged as space helmets
            if (headItem.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "space_helmets")))) {
                hasProtection = true;
            }
            
            // Compatibility with Create diving gear: Helmet + Backtank/Jetpack
            ResourceLocation headId = BuiltInRegistries.ITEM.getKey(headItem.getItem());
            if (headId.getNamespace().equals("create") && headId.getPath().contains("diving_helmet")) {
                ResourceLocation chestId = BuiltInRegistries.ITEM.getKey(chestItem.getItem());
                if ((chestId.getNamespace().equals("create") && chestId.getPath().contains("backtank")) || 
                    chestId.getPath().contains("jetpack")) {
                    hasProtection = true;
                }
            }

            if (!hasProtection) {
                int air = entity.getAirSupply();
                
                // Rapidly deplete air supply
                entity.setAirSupply(air - 5); 
                
                if (entity.getAirSupply() <= -20) {
                    entity.setAirSupply(0);
                    entity.hurt(entity.level().damageSources().drown(), 2.0f);
                }
            }
        }
    }

    private static void applyFallingHeatDamage(LivingEntity entity) {
        double y = entity.getY();
        if (y > REENTRY_HEAT_START_Y && y < REENTRY_HEAT_END_Y && entity.getDeltaMovement().y() < -3.0) {
            double speed = -entity.getDeltaMovement().y();
            float intensity = (float) Math.clamp((speed - 3.0) / 1.0, 0.0, 1.0);
            
            if (intensity > 0.1) {
                entity.setRemainingFireTicks(40);
                if (entity.level().getGameTime() % 10 == 0) {
                    entity.hurt(entity.level().damageSources().onFire(), intensity * 2.0f);
                }
                if (entity.level().isClientSide) {
                    spawnHeatParticles(entity);
                }
            }
        }
    }

    private static void spawnHeatParticles(Entity entity) {
        for (int i = 0; i < 5; i++) {
            entity.level().addParticle(RocketParticles.BLUE_FLAME.get(), 
                entity.getX(), entity.getY(), entity.getZ(), 0, 0.1, 0);
        }
    }
}
