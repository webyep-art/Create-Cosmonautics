package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.devce.rocketnautics.content.blocks.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ThrusterPeripheral implements IPeripheral {
    private final IThruster thruster;
    private final String engineType;

    public ThrusterPeripheral(IThruster thruster) {
        this.thruster = thruster;
        if (thruster instanceof VectorThrusterBlockEntity) {
            this.engineType = "vector_thruster";
        } else if (thruster instanceof RCSThrusterBlockEntity) {
            this.engineType = "rcs_thruster";
        } else if (thruster instanceof RocketThrusterBlockEntity) {
            this.engineType = "rocket_thruster";
        } else if (thruster instanceof BoosterThrusterBlockEntity) {
            this.engineType = "booster_thruster";
        } else {
            this.engineType = "generic_thruster";
        }
    }

    @NotNull
    @Override
    public String getType() {
        return "rocket_engine";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ThrusterPeripheral && ((ThrusterPeripheral) other).thruster == this.thruster;
    }

    @LuaFunction(mainThread = true)
    public final boolean isActive() {
        return thruster.isActive();
    }

    @LuaFunction(mainThread = true)
    public final void setActive(boolean active) {
        thruster.setActive(active);
    }

    @LuaFunction(mainThread = true)
    public final float getThrust() {
        if (thruster instanceof RocketThrusterBlockEntity rt) {
            return rt.getCurrentPower() * 10.0f;
        } else if (thruster instanceof BoosterThrusterBlockEntity bt) {
            return bt.getThrustPower().getValue() * 10.0f;
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    public final void setThrust(int power) {
        var behaviour = thruster.getThrustPower();
        if (behaviour != null) {
            behaviour.setValue(Math.max(0, Math.min(50, power / 10)));
        }
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getData() {
        Map<String, Object> data = new HashMap<>();
        data.put("active", thruster.isActive());
        data.put("engine_type", engineType);
        
        if (thruster instanceof RocketThrusterBlockEntity rt) {
            data.put("throttle", rt.fuelThrottle);
            data.put("fuel_usage", rt.currentFuelUsage);
            data.put("fuel_amount", rt.fuelTank.getFluidAmount());
            data.put("fuel_capacity", rt.fuelTank.getCapacity());
            data.put("fuel_name", rt.fuelTank.getFluid().getHoverName().getString());
            data.put("ignition_ticks", rt.ignitionTicks);
            data.put("warmup_time", rt.getWarmupTime());
            data.put("isp_multiplier", rt.getCurrentIspMultiplier());
            data.put("efficiency_multiplier", rt.getCurrentEfficiencyMultiplier());
            data.put("is_steam_mode", rt.isSteamMode());
            data.put("startup_ticks", rt.getStartupTicks());
            data.put("burnout_delay", rt.getBurnoutDelay());
            
            if (rt instanceof VectorThrusterBlockEntity vt) {
                Map<String, Float> gimbal = new HashMap<>();
                gimbal.put("x", vt.getGimbalX());
                gimbal.put("y", vt.getGimbalY());
                gimbal.put("z", vt.getGimbalZ());
                data.put("gimbal", gimbal);
            }
        } else if (thruster instanceof BoosterThrusterBlockEntity bt) {
            data.put("ignition_ticks", bt.ignitionTicks);
            data.put("thrust_power", bt.getThrustPower().getValue() * 10);
            data.put("ignited", bt.isIgnited());
            data.put("is_spent", bt.isSpent());
            data.put("fuel_ticks", bt.getFuelTicks());
        }
        
        return data;
    }

    @LuaFunction(mainThread = true)
    public final float getFuelThrottle() {
        if (thruster instanceof RocketThrusterBlockEntity rt) {
            return rt.fuelThrottle;
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    public final int getFuelAmount() {
        if (thruster instanceof RocketThrusterBlockEntity rt) {
            return rt.fuelTank.getFluidAmount();
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    public final String getFuelName() {
        if (thruster instanceof RocketThrusterBlockEntity rt) {
            return rt.fuelTank.getFluid().getHoverName().getString();
        }
        return "None";
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Float> getGimbal() {
        if (thruster instanceof VectorThrusterBlockEntity vt) {
            Map<String, Float> gimbal = new HashMap<>();
            gimbal.put("x", vt.getGimbalX());
            gimbal.put("y", vt.getGimbalY());
            gimbal.put("z", vt.getGimbalZ());
            return gimbal;
        }
        return null;
    }

    @LuaFunction(mainThread = true)
    public final void setGimbal(double x, double y, double z) {
        if (thruster instanceof VectorThrusterBlockEntity vt) {
            vt.setComputerGimbal((float)x, (float)y, (float)z);
        }
    }
}
