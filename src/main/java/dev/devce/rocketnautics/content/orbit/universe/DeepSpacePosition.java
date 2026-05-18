package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import net.minecraft.network.FriendlyByteBuf;
import org.hipparchus.analysis.UnivariateFunction;
import org.hipparchus.analysis.solvers.BrentSolver;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.Collection;
import java.util.Optional;

public class DeepSpacePosition {
    private static final Orbit FALLBACK = new CartesianOrbit(new TimeStampedPVCoordinates(DeepSpaceData.EPOCH, Vector3D.PLUS_I, Vector3D.PLUS_J), Frame.getRoot(), 1);
    private static final BrentSolver SOLVER = new BrentSolver();

    // generated fields (for read/write operations)
    private @NotNull Orbit currentOrbit = FALLBACK;
    private double roi = Double.NaN;

    // saved fields (for read/write operations)
    private int timescale = 1;
    private long localUniverseTicks;

    public DeepSpacePosition() {}

    public void init(UniverseDefinition universe, String frameName, TimeStampedPVCoordinates coords) {
        Optional<Frame> frame = universe.getFrameByName(frameName);
        if (frame.isPresent() && coords != null) {
            init(universe, frame.get(), coords);
        }
    }

    public void init(UniverseDefinition universe, @NotNull Frame frame, @NotNull TimeStampedPVCoordinates coords) {
        PointGravitySource controlling = determineControllingGravitySource(coords, frame, universe);
        setCurrentOrbit(createOrbitAroundSource(coords, frame, controlling), controlling.roi());
    }

    public boolean isCorrupted() {
        return currentOrbit == FALLBACK;
    }

    /**
     * Do the complete calculations to propagate this position forward in time. Note that this is a <b>mutating</b> operation;
     * if modification is not desired, use {@link #copy()} first.
     * @param universe the universe to propagate through.
     * @return this object, mutated, for convenience.
     */
    public DeepSpacePosition propagate(UniverseDefinition universe) {
        // check if we have entered the domain of a different gravity source;
        // if so, find out exactly when and update our orbit.
        Frame currentFrame = currentOrbit.getFrame();
        PointGravitySource shouldControlling = determineControllingGravitySource(currentOrbit.getPVCoordinates(DeepSpaceData.getTime(localUniverseTicks + timescale), currentFrame), currentFrame, universe);
        if (shouldControlling.orekitFrame() != currentFrame) {
            // we want to find the point on the current orbit that intersects the smaller sphere of influence.
            // we know the point is between the time we were at and the time we are now at.
            // we solve this numerically via Brent's Method.
            AbsoluteDate startTime = DeepSpaceData.getTime(localUniverseTicks);
            double roi = Math.min(this.roi, shouldControlling.roi());
            UnivariateFunction func = t -> {
                AbsoluteDate time = startTime.shiftedBy(t / 20); // convert from floating ticks to seconds
                return currentOrbit.getPosition(time, currentOrbit.getFrame()).getNormSq() - roi * roi;
            };
            // verify that there is supposed to be a zero
            double lower = func.value(0);
            double upper = func.value(timescale);
            TimeStampedPVCoordinates transitionCoords;
            if (lower * upper <= 0) {
                // our level of accuracy is controlled by the maximum evaluations.
                // would it be better to instead set an absolute accuracy of 0.5,
                // then round the solved time to the nearest tick?
                double transitionTicks = SOLVER.solve(10, func, 0, timescale);
                AbsoluteDate transitionTime = startTime.shiftedBy(transitionTicks / 20);
                // construct a new orbit, starting from our position at the transition time.
                transitionCoords = currentOrbit.getPVCoordinates(transitionTime, shouldControlling.orekitFrame());
            } else {
                transitionCoords = currentOrbit.getPVCoordinates(startTime, shouldControlling.orekitFrame());

            }
            setCurrentOrbit(createOrbitAroundSource(transitionCoords, shouldControlling.orekitFrame(), shouldControlling), shouldControlling.roi());
        }
        localUniverseTicks += timescale;
        return this;
    }

    private PointGravitySource determineControllingGravitySource(TimeStampedPVCoordinates coords, Frame frame, UniverseDefinition universe) {
        // the controlling object is the one with the smallest ROI that we are inside.
        Collection<PointGravitySource> sources = universe.getGravitySources();
        PointGravitySource controlling = null;
        double smallestRoi = Double.POSITIVE_INFINITY;
        double controllingDistance2 = Double.NaN;
        for (PointGravitySource source : sources) {
            if (source.roi() > smallestRoi) continue; // cannot win
            Vector3D ourPositionInBodyFrame = source.posInMyFrame(coords.getDate(), coords.getPosition(), frame);
            double d2 = ourPositionInBodyFrame.getNormSq();
            if (source.roi() * source.roi() >= d2) {
                // tiebreaker, mostly for objects whose ROI is infinite
                if (source.roi() == smallestRoi) {
                    if (controllingDistance2 <= d2) {
                        continue;
                    }
                }
                controlling = source;
                smallestRoi = source.roi();
                controllingDistance2 = d2;
            }
        }
        return controlling;
    }

    /**
     * Constructs a new keplerian orbit around the gravity source.
     * @param currentCoords The current coordinates with which to generate the orbit.
     * @param currentFrame the frame in which currentCoords is defined.
     * @param source The gravity source to orbit.
     * @return an orbit centered around the source's frame.
     */
    private Orbit createOrbitAroundSource(TimeStampedPVCoordinates currentCoords, Frame currentFrame, PointGravitySource source) {
        return createOrbitAroundSource(currentCoords, currentFrame, source, Vector3D.ZERO);
    }

    /**
     * Constructs a new keplerian orbit around the gravity source.
     * @param currentCoords The current coordinates with which to generate the orbit.
     * @param currentFrame the frame in which currentCoords is defined.
     * @param source The gravity source to orbit.
     * @param nonKeplerianAcceleration the non-keplerian acceleration to introduce to the orbit.
     *                                 The acceleration in currentCoords will be ignored.
     * @return an orbit centered around the source's frame.
     */
    private Orbit createOrbitAroundSource(TimeStampedPVCoordinates currentCoords, Frame currentFrame, PointGravitySource source, Vector3D nonKeplerianAcceleration) {
        // revise coordinates to be in the gravity source's frame.
        if (currentFrame != source.orekitFrame()) {
            currentCoords = currentFrame.getTransformTo(source.orekitFrame(), currentCoords.getDate()).transformPVCoordinates(currentCoords);
        }
        if (!nonKeplerianAcceleration.equals(currentCoords.getAcceleration())) {
            currentCoords = new TimeStampedPVCoordinates(currentCoords.getDate(), currentCoords.getPosition(), currentCoords.getVelocity());
        }
        return new KeplerianOrbit(currentCoords, source.orekitFrame(), source.mu());
    }

    public void setCurrentOrbit(@NotNull Orbit currentOrbit, double roi) {
        this.currentOrbit = currentOrbit;
        this.roi = roi;
    }

    public @NotNull Orbit getCurrentOrbit() {
        return currentOrbit;
    }

    public void setTimescale(int timescale) {
        this.timescale = timescale;
    }

    public int getTimescale() {
        return timescale;
    }

    public void setLocalUniverseTicks(long localUniverseTicks) {
        this.localUniverseTicks = localUniverseTicks;
    }

    public long getLocalUniverseTicks() {
        return localUniverseTicks;
    }

    public AbsoluteDate getLocalUniverseTime() {
        return DeepSpaceData.getTime(localUniverseTicks);
    }

    public Frame getFrame() {
        return getCurrentOrbit().getFrame();
    }

    public TimeStampedPVCoordinates getCurrentPVCoords() {
        return getPVCoords(getLocalUniverseTime());
    }

    public Vector3D getCurrentPosition() {
        return getPosition(getLocalUniverseTime());
    }

    /**
     * Performs simple calculation of where we would be on our current orbit at the target date.
     * For full physics propagation, use {@link #propagate(UniverseDefinition)} instead.
     */
    public TimeStampedPVCoordinates getPVCoords(AbsoluteDate date) {
        return getCurrentOrbit().getPVCoordinates(date, getCurrentOrbit().getFrame());
    }

    public Vector3D getPosition(Frame inFrame) {
        return getCurrentOrbit().getPosition(getLocalUniverseTime(), inFrame);
    }

    /**
     * Performs simple calculation of where we would be on our current orbit at the target date.
     * For full physics propagation, use {@link #propagate(UniverseDefinition)} instead.
     */
    public Vector3D getPosition(AbsoluteDate date) {
        return getCurrentOrbit().getPosition(date, getCurrentOrbit().getFrame());
    }

    public void write(FriendlyByteBuf buf, UniverseDefinition universe) {
        DeepSpaceHelper.STAMPED_PVCOORDS_CODEC_S.encode(buf, getCurrentOrbit().getPVCoordinates(getLocalUniverseTime(), getCurrentOrbit().getFrame()));
        buf.writeVarInt(universe.getFrameIDByName(getCurrentOrbit().getFrame().getName()));
        buf.writeVarInt(timescale);
        buf.writeVarLong(localUniverseTicks);
    }

    /**
     * @return whether the read was successful.
     */
    public boolean read(FriendlyByteBuf buf, UniverseDefinition universe) {
        TimeStampedPVCoordinates coords = DeepSpaceHelper.STAMPED_PVCOORDS_CODEC_S.decode(buf);
        int id = buf.readVarInt();
        Optional<Frame> frame = universe.getFrameByID(id);
        if (frame.isEmpty()) return false;
        init(universe, frame.get(), coords);
        setTimescale(buf.readVarInt());
        setLocalUniverseTicks(buf.readVarLong());
        return true;
    }

    public DeepSpacePosition copy() {
        DeepSpacePosition pos = new DeepSpacePosition();
        copyTo(pos);
        return pos;
    }


    public void copyTo(DeepSpacePosition dest) {
        dest.currentOrbit = this.currentOrbit; // orbits are immutable, so this is safe
        dest.roi = this.roi;
        dest.timescale = this.timescale;
        dest.localUniverseTicks = this.localUniverseTicks;
    }

    public void reset() {
        this.currentOrbit = FALLBACK;
        this.roi = Double.NaN;
        this.timescale = 1;
        this.localUniverseTicks = 0;
    }
}
