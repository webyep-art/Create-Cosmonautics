package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import dev.devce.rocketnautics.api.peripherals.IPeripheral;

public interface IThruster extends IPeripheral {
    boolean isActive();
    boolean isRemoved();
    BlockPos getBlockPos();
    Level getLevel();
    ScrollValueBehaviour getThrustPower();
    void setActive(boolean active);
    void setThrottle(float throttle);
    void setGimbal(double pitch, double yaw);
    float getFlow();

    @Override
    default String getPeripheralType() {
        return "thruster";
    }

    @Override
    default double readValue(String key) {
        if (key.equals("thrust")) return getFlow() * 100.0;
        return 0;
    }

    @Override
    default void writeValue(String key, double value) {
        if (key.equals("throttle")) {
            setActive(value > 0);
            setThrottle((float) value);
        } else if (key.equals("thrust")) {
            setActive(value > 0);
            var behavior = getThrustPower();
            if (behavior != null) {
                float maxN = behavior.getValue() * 10.0f;
                setThrottle(maxN > 0 ? (float)(value / maxN) : 0);
            } else {
                setThrottle((float) value);
            }
        }
    }

    @Override
    default void writeValues(String key, double... values) {
        if (key.equals("gimbal") && values.length >= 2) {
            setGimbal(values[0], values[1]);
        }
    }
}
