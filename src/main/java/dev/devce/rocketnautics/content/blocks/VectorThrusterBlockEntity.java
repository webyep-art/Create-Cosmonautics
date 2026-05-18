package dev.devce.rocketnautics.content.blocks;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public class VectorThrusterBlockEntity extends RocketThrusterBlockEntity {
    @Override
    public String getPeripheralType() {
        return "vector_engine";
    }

    @Override
    public double readValue(String key) {
        if (key.equals("thrust")) return getFlow() * 100.0;
        if (key.equals("gimbal_x")) return gimbalX;
        if (key.equals("gimbal_z")) return gimbalZ;
        return 0;
    }
    private static final Direction[] DIRECTIONS = Direction.values();

    private float gimbalX = 0;
    private float gimbalY = 0;
    private float gimbalZ = 0;

    private float prevGimbalX = 0;
    private float prevGimbalY = 0;
    private float prevGimbalZ = 0;

    private float ccGimbalX = 0;
    private float ccGimbalY = 0;
    private float ccGimbalZ = 0;

    public VectorThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getWarmupTime() {
        return 10;
    }

    @Override
    public Vector3d getParticleDirection() {
        Direction nozzle = getThrustDirection();
        return new Vector3d(
                nozzle.getStepX() + gimbalX,
                nozzle.getStepY() + gimbalY,
                nozzle.getStepZ() + gimbalZ).normalize();
    }

    private int ccGimbalTimeout = 0;

    public void setComputerGimbal(float x, float y, float z) {
        this.ccGimbalX = Math.max(-1.0f, Math.min(1.0f, x));
        this.ccGimbalY = Math.max(-1.0f, Math.min(1.0f, y));
        this.ccGimbalZ = Math.max(-1.0f, Math.min(1.0f, z));
        this.ccGimbalTimeout = 5;
        setChanged();
    }



    public void updateGimbalAngles() {
        if (level == null)
            return;
            
        if (ccGimbalTimeout > 0) {
            ccGimbalTimeout--;
        } else {
            ccGimbalX *= 0.8f;
            ccGimbalY *= 0.8f;
            ccGimbalZ *= 0.8f;
            if (Math.abs(ccGimbalX) < 0.001f) ccGimbalX = 0;
            if (Math.abs(ccGimbalY) < 0.001f) ccGimbalY = 0;
            if (Math.abs(ccGimbalZ) < 0.001f) ccGimbalZ = 0;
        }
            
        Direction nozzle = getThrustDirection();

        float gX = ccGimbalX;
        float gY = ccGimbalY;
        float gZ = ccGimbalZ;

        for (Direction dir : DIRECTIONS) {
            if (dir.getAxis() != nozzle.getAxis()) {
                int signal = level.getSignal(worldPosition.relative(dir), dir);
                float strength = signal * 0.033f;
                gX += dir.getStepX() * strength;
                gY += dir.getStepY() * strength;
                gZ += dir.getStepZ() * strength;
            }
        }

        gX = Math.max(-1.0f, Math.min(1.0f, gX));
        gY = Math.max(-1.0f, Math.min(1.0f, gY));
        gZ = Math.max(-1.0f, Math.min(1.0f, gZ));

        if (Math.abs(gimbalX - gX) > 0.001f || Math.abs(gimbalY - gY) > 0.001f || Math.abs(gimbalZ - gZ) > 0.001f) {
            gimbalX = gX;
            gimbalY = gY;
            gimbalZ = gZ;
            if (!level.isClientSide) {
                sendData();
                setChanged();
            }
        }
    }

    @Override
    public void setGimbal(double val1, double val2) {
        // Direct X and Z mapping as requested (-180..180 range)
        float xOffset = (float) (val1 / 180.0);
        float zOffset = (float) (val2 / 180.0);

        setComputerGimbal(xOffset, 0, zOffset);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VectorThrusterBlockEntity blockEntity) {
        blockEntity.prevGimbalX = blockEntity.gimbalX;
        blockEntity.prevGimbalY = blockEntity.gimbalY;
        blockEntity.prevGimbalZ = blockEntity.gimbalZ;

        blockEntity.updateGimbalAngles();

        RocketThrusterBlockEntity.tick(level, pos, state, blockEntity);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel serverSubLevel, RigidBodyHandle handle, double deltaTime) {
        if (!isActive())
            return;

        Direction facing = getThrustDirection();
        Direction pushDirection = facing.getOpposite();

        double vx = pushDirection.getStepX() - gimbalX;
        double vy = pushDirection.getStepY() - gimbalY;
        double vz = pushDirection.getStepZ() - gimbalZ;

        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 0.001)
            return;

        double currentThrust = getCurrentPower() * 10.0;
        Vector3d thrustVector = new Vector3d(vx / length, vy / length, vz / length).mul(currentThrust);

        Vector3d blockCenter = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5);
        handle.applyImpulseAtPoint(blockCenter, thrustVector.mul(deltaTime));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("GimbalX", gimbalX);
        tag.putFloat("GimbalY", gimbalY);
        tag.putFloat("GimbalZ", gimbalZ);
        tag.putFloat("CCGimbalX", ccGimbalX);
        tag.putFloat("CCGimbalY", ccGimbalY);
        tag.putFloat("CCGimbalZ", ccGimbalZ);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        gimbalX = tag.getFloat("GimbalX");
        gimbalY = tag.getFloat("GimbalY");
        gimbalZ = tag.getFloat("GimbalZ");
        ccGimbalX = tag.getFloat("CCGimbalX");
        ccGimbalY = tag.getFloat("CCGimbalY");
        ccGimbalZ = tag.getFloat("CCGimbalZ");
    }

    @Override
    public void writeValue(String key, double value) {
        if ("throttle".equals(key)) {
            setActive(value > 0);
            setThrottle((float) value);
        } else if ("thrust".equals(key)) {
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
    public void writeValues(String key, double... values) {
        if ("gimbal".equals(key) && values.length >= 2) {
            setComputerGimbal((float) values[0], 0.0f, (float) values[1]);
        }
    }

    public float getPrevGimbalX() {
        return prevGimbalX;
    }

    public float getPrevGimbalY() {
        return prevGimbalY;
    }

    public float getPrevGimbalZ() {
        return prevGimbalZ;
    }

    public float getGimbalX() {
        return gimbalX;
    }

    public float getGimbalY() {
        return gimbalY;
    }

    public float getGimbalZ() {
        return gimbalZ;
    }
}
