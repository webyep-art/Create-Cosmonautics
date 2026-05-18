package dev.devce.rocketnautics.content.blocks;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlock;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import dev.simulated_team.simulated.data.SimLang;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.orekit.frames.Frame;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class HologramTableBlockEntity extends SmartBlockEntity {

    public HologramTableScrollValueBehavior scrollBehavior;

    public HologramTableBlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
        super(p_155228_, p_155229_, p_155230_);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scrollBehavior = new HologramTableScrollValueBehavior(this);
        behaviours.add(scrollBehavior);
    }

    public int getHoloSize() {
        return scrollBehavior.primaryValue + 1;
    }

    public double getHoloScale() {
        return Math.pow(2, scrollBehavior.secondaryValue);
    }

    public static class HologramTableScrollValueBehavior extends ScrollValueBehaviour {
        protected Direction lastSide = Direction.NORTH;
        protected int primaryValue;
        protected int secondaryValue;
        protected Function<Integer, String> formatter;

        public HologramTableScrollValueBehavior(final HologramTableBlockEntity be) {
            super(Component.empty(), be, new HologramTableValueBox(be));
            this.primaryValue = 2;
            this.secondaryValue = 24;
        }

        public boolean isPrimaryAxis() {
            return this.lastSide.getAxis().isHorizontal() && this.lastSide.getAxis() != Direction.Axis.X;
        }

        private int min() {
            return 0; // note -- the renderer is hardcoded to go from 0 to max
        }

        private int max() {
            return isPrimaryAxis() ? 8 : 64;
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(isPrimaryAxis() ? Component.translatable("gui.rocketnautics.holo_size") : Component.translatable("gui.rocketnautics.holo_scale"),
                    max(), 10, ImmutableList.of(Component.literal("Value")),
                    new ValueSettingsFormatter(s -> isPrimaryAxis() ? CreateLang.number(s.value() + 1).component() : Component.literal(String.format("%.2em", Math.pow(2, s.value())))));
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, Math.abs(this.getValue()));
        }

        @Override
        public void write(final CompoundTag nbt, final HolderLookup.Provider registries, final boolean clientPacket) {
            nbt.putInt("ScrollValue1", this.primaryValue);
            nbt.putInt("ScrollValue2", this.secondaryValue);
            super.write(nbt, registries, clientPacket);
        }

        @Override
        public void read(final CompoundTag nbt, final HolderLookup.Provider registries, final boolean clientPacket) {
            this.primaryValue = nbt.getInt("ScrollValue1");
            this.secondaryValue = nbt.getInt("ScrollValue2");
            if (clientPacket) {

            }
            super.read(nbt, registries, clientPacket);
        }

        @Override
        public boolean writeToClipboard(HolderLookup.@NotNull Provider registries, CompoundTag tag, Direction side) {
            if(!acceptsValueSettings())
                return false;
            tag.putInt("ScrollValue1", this.primaryValue);
            tag.putInt("ScrollValue2", this.secondaryValue);
            return true;
        }

        @Override
        public boolean readFromClipboard(HolderLookup.@NotNull Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
            if(!acceptsValueSettings()) return false;
            if(!tag.contains("ScrollValue1") || !tag.contains("ScrollValue2")) return true;
            if(simulate) return true;
            this.primaryValue = tag.getInt("ScrollValue1");
            this.secondaryValue = tag.getInt("ScrollValue2");
            blockEntity.setChanged();
            blockEntity.sendData();
            return true;
        }

        @Override
        public int getValue() {
            return this.isPrimaryAxis() ? this.primaryValue : this.secondaryValue;
        }

        public void setValue(int value) {
            value = Mth.clamp(value, min(), max());
            if (value == this.getValue())
                return;
            if (this.isPrimaryAxis())
                this.primaryValue = value;
            else
                this.secondaryValue = value;
            this.blockEntity.setChanged();
            this.blockEntity.sendData();
        }

        @Override
        public String formatValue() {
            return isPrimaryAxis() ? String.valueOf(getValue() + 1) : String.format("%.2em", Math.pow(2, getValue()));
        }

        @Override
        public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
            secondaryValue += isPrimaryAxis() ? 1 : -1;
            this.blockEntity.setChanged();
            this.blockEntity.sendData();
        }
    }

    public static class HologramTableValueBox extends ValueBoxTransform.Sided {
        HologramTableBlockEntity be;

        public HologramTableValueBox(final HologramTableBlockEntity be) {
            this.be = be;
        }

        public Sided fromSide(final Direction direction) {
            this.direction = direction;
            this.be.scrollBehavior.lastSide = direction;
            return this;
        }

        @Override
        protected boolean isSideActive(final BlockState state, final Direction direction) {
            return direction.getAxis().isHorizontal();
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 16);
        }

        @Override
        public boolean testHit(final LevelAccessor level, final BlockPos pos, final BlockState state, final Vec3 localHit) {
            final Vec3 offset = this.getLocalOffset(level, pos, state);

            if (offset == null)
                return false;

            return localHit.distanceTo(offset) < this.scale / 1.5f;
        }
    }
}
