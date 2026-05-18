package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.client.PlanetColors;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.api.orbit.FrameTreeOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedAngularCoordinates;

import java.util.function.IntFunction;

// note -- render data supplier is never synced to client
public record CubePlanet(@NotNull FrameTree frame, double radius, TimeStampedAngularCoordinates rotationDescription,
                         @Nullable PlanetDimensionData linkedDimension, @Nullable IntFunction<byte[]> renderDataSupplier, boolean clouds) implements FrameTreeOwner {

    public byte[] getRenderData(int powerScaleClamp) {
        if (renderDataSupplier == null) return PlanetColors.BLANK;
        return renderDataSupplier.apply(powerScaleClamp);
    }

    public Rotation getRotationAtTime(AbsoluteDate date) {
        return rotationDescription.rotationShiftedBy(date.durationFrom(rotationDescription.getDate()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(frame.getId());
        buf.writeDouble(radius);
        DeepSpaceHelper.STAMPED_ANGULARCOORDS_CODEC_S.encode(buf, rotationDescription);
        buf.writeBoolean(clouds);
        buf.writeBoolean(linkedDimension != null);
        if (linkedDimension != null) {
            linkedDimension.write(buf);
        }
    }

    public static CubePlanet read(FriendlyByteBuf buf, FrameTree frameSource) {
        int id = buf.readVarInt();
        double radius = buf.readDouble();
        TimeStampedAngularCoordinates coords = DeepSpaceHelper.STAMPED_ANGULARCOORDS_CODEC_S.decode(buf);
        boolean clouds = buf.readBoolean();
        PlanetDimensionData linkedDimension = null;
        if (buf.readBoolean()) {
            linkedDimension = PlanetDimensionData.read(buf);
        }
        return new CubePlanet(frameSource.getInTreeByID(id).get(), radius, coords, linkedDimension, null, clouds);
    }
}
