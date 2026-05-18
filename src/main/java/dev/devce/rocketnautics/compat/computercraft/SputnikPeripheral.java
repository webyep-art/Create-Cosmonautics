package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Quaterniond;

public class SputnikPeripheral implements IPeripheral {
    private final SputnikBlockEntity sputnik;

    public SputnikPeripheral(SputnikBlockEntity sputnik) {
        this.sputnik = sputnik;
    }

    @NotNull
    @Override
    public String getType() {
        return "sputnik";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof SputnikPeripheral && ((SputnikPeripheral) other).sputnik == this.sputnik;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getBiomeInfo() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", sputnik.getBiomeName());
        data.put("color", sputnik.getBiomeColor());
        return data;
    }

    @LuaFunction(mainThread = true)
    public final String getBiomeName() {
        return sputnik.getBiomeName();
    }

    @LuaFunction(mainThread = true)
    public final int getBiomeColor() {
        return sputnik.getBiomeColor();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Double> getGlobalPos() {
        org.joml.Vector3d pos = sputnik.getGlobalPos();
        Map<String, Double> data = new HashMap<>();
        data.put("x", pos.x);
        data.put("y", pos.y);
        data.put("z", pos.z);
        return data;
    }

    @LuaFunction(mainThread = true)
    public final String getGlobalBiomeName() {
        return sputnik.getGlobalBiomeName();
    }

    @LuaFunction(mainThread = true)
    public final int getGlobalBiomeColor() {
        return sputnik.getGlobalBiomeColor();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getPhysics() {
        Map<String, Object> data = new HashMap<>();
        if (sputnik.getLevel() == null) return data;
        
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(sputnik.getLevel(), sputnik.getBlockPos());
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            // Mass
            data.put("mass", serverSubLevel.getMassTracker().getMass());
            
            // Pose (Position & Orientation)
            var pose = serverSubLevel.logicalPose();
            var lastPose = serverSubLevel.lastPose();
            
            // Orientation Quaternions
            Map<String, Double> quat = new HashMap<>();
            quat.put("w", pose.orientation().w());
            quat.put("x", pose.orientation().x());
            quat.put("y", pose.orientation().y());
            quat.put("z", pose.orientation().z());
            data.put("quaternion", quat);
            
            // Euler Angles (Pitch, Yaw, Roll in degrees)
            Vector3d euler = pose.orientation().getEulerAnglesXYZ(new Vector3d());
            Map<String, Double> angles = new HashMap<>();
            angles.put("pitch", Math.toDegrees(euler.x));
            angles.put("yaw", Math.toDegrees(euler.y));
            angles.put("roll", Math.toDegrees(euler.z));
            data.put("euler", angles);
            
            // Velocity (Calculated from position delta over 1 tick)
            Vector3d velocity = new Vector3d(pose.position()).sub(lastPose.position()).mul(20.0);
            Map<String, Double> velData = new HashMap<>();
            velData.put("x", velocity.x);
            velData.put("y", velocity.y);
            velData.put("z", velocity.z);
            data.put("velocity", velData);
            
            // Local Gravity approximation (if needed by flight computers)
            boolean inSpace = sputnik.getLevel().dimension() == dev.devce.rocketnautics.content.physics.SpaceTransitionHandler.SPACE_DIM;
            data.put("gravity", inSpace ? 0.0 : -9.81);
        }
        
        return data;
    }
}
