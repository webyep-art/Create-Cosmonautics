package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.api.orbit.FrameTreeOwner;
import net.minecraft.network.FriendlyByteBuf;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;

public record PointGravitySource(@NotNull FrameTree frame, double mu, double roi) implements PVCoordinatesProvider, FrameTreeOwner {

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(frame.getId());
        buf.writeDouble(mu);
        buf.writeDouble(roi);
    }

    public static PointGravitySource read(FriendlyByteBuf buf, FrameTree frameSource) {
        int id = buf.readVarInt();
        double mu = buf.readDouble();
        double roi = buf.readDouble();
        return new PointGravitySource(frameSource.getInTreeByID(id).get(), mu, roi);
    }
}
