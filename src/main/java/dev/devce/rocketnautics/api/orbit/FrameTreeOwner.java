package dev.devce.rocketnautics.api.orbit;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;

public interface FrameTreeOwner extends PVCoordinatesProvider {

    FrameTree frame();

    default int id() {
        return frame().getId();
    }

    default Frame orekitFrame() {
        return frame().getOrekitFrame();
    }

    default Vector3D posInMyFrame(AbsoluteDate date, Vector3D coords, Frame coordsFrame) {
        return coordsFrame.getStaticTransformTo(orekitFrame(), date).transformPosition(coords);
    }

    default PVCoordinates coordsInMyFrame(AbsoluteDate date, PVCoordinates coords, Frame coordsFrame) {
        return coordsFrame.getTransformTo(orekitFrame(), date).transformPVCoordinates(coords);
    }

    default TimeStampedPVCoordinates coordsInMyFrame(TimeStampedPVCoordinates coords, Frame coordsFrame) {
        return coordsFrame.getTransformTo(orekitFrame(), coords.getDate()).transformPVCoordinates(coords);
    }

    @Override
    default Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
        return orekitFrame().getStaticTransformTo(frame, date).transformPosition(Vector3D.ZERO);
    }

    @Override
    default Vector3D getVelocity(final AbsoluteDate date, final Frame frame) {
        final PVCoordinates pvCoordinates = orekitFrame().getKinematicTransformTo(frame, date).transformOnlyPV(PVCoordinates.ZERO);
        return pvCoordinates.getVelocity();
    }

    @Override
    default TimeStampedPVCoordinates getPVCoordinates(AbsoluteDate date, Frame frame) {
        PVCoordinates coords = orekitFrame().getTransformTo(frame, date).transformPVCoordinates(PVCoordinates.ZERO);
        return new TimeStampedPVCoordinates(date, coords);
    }
}
