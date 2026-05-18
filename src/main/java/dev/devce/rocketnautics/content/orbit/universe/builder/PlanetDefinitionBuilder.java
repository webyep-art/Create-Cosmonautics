package dev.devce.rocketnautics.content.orbit.universe.builder;

import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.content.orbit.*;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedAngularCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.function.IntFunction;

public class PlanetDefinitionBuilder {
    private final UniverseDefinitionBuilder parent;
    private @Nullable ResourceKey<Level> linkedDimension;
    private int transferHeight = 20_000;
    private @Nullable IntFunction<byte[]> renderDataOverride;
    private boolean clouds = false;

    private double radius;
    private String frameName;

    private TimeStampedAngularCoordinates angularCoordinates;
    private Vector3d rotationAxis;

    private int ticksPerRevolution;
    private boolean tidalLock;

    private double accelerationAtSurface;
    private double mu;

    private PointGravitySource orbited;
    private TimeStampedPVCoordinates orbitCoords;

    private FrameTree fixedPositionParentFrame;
    private Vector3D fixedPosition;

    public PlanetDefinitionBuilder(UniverseDefinitionBuilder parent) {
        this.parent = parent;
    }

    public void build() {
        if (radius <= 0 || frameName == null) {
            throw new IllegalStateException("Builder not fully/properly initialized!");
        }
        // mu / radius^2 = acceleration at surface
        if (mu <= 0) {
            if (accelerationAtSurface <= 0) {
                throw new IllegalStateException("Builder not fully/properly initialized!");
            }
            mu = accelerationAtSurface * radius * radius;
        }
        FrameTree ourFrame;
        KeplerianOrbit orbit = null;
        double roi;
        if (orbited != null) {
            orbit = new KeplerianOrbit(orbitCoords, orbited.orekitFrame(), orbited.mu());
            ourFrame = orbited.frame().createChild(frameName, orbit);
            roi = orbit.getA() * Math.pow(mu / orbited.mu(), 2/5d);
        } else if (fixedPositionParentFrame != null) {
            ourFrame = fixedPositionParentFrame.createChild(frameName, fixedPosition);
            roi = Double.POSITIVE_INFINITY;
        } else {
            throw new IllegalStateException("Builder does not have an orbit or a fixed position!");
        }
        if (ourFrame == null) {
            throw new IllegalStateException("Builder's configured name is already reserved!");
        }
        if (this.angularCoordinates == null) {
            if (tidalLock && orbited != null) {
                assert orbit != null;
                double periodSeconds = orbit.getKeplerianPeriod();
                ticksPerRevolution = (int) (periodSeconds * 20);
                // TODO check if this needs to be negated to properly tidal lock
                rotationAxis = DeepSpaceHelper.adapt(orbit.getPVCoordinates().getMomentum());
            } else if (ticksPerRevolution <= 0 || rotationAxis == null) {
                throw new IllegalStateException("Builder does not have a revolution time configured!");
            }
            // compute angular coordinates
            // magnitude of rotation axis needs to be angular velocity, or 2pi / period in seconds
            double mul = 40 * Math.PI / (ticksPerRevolution * rotationAxis.length());
            Vector3D angVel = new Vector3D(rotationAxis.x * mul, rotationAxis.y * mul, rotationAxis.z * mu);
            this.angularCoordinates = new TimeStampedAngularCoordinates(DeepSpaceData.EPOCH, Rotation.IDENTITY, angVel, Vector3D.ZERO);
        }
        if (renderDataOverride == null) {
            if (linkedDimension == null) {
                throw new IllegalStateException("Builder does not have a render option available!");
            }
            ResourceKey<Level> v = linkedDimension;
            renderDataOverride = i -> {
                ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(v);
                return SkyDataHandler.getHandlerForLevel(level).getRenderDataForDeepSpace(i);
            };
        }
        parent.gravitySource(new PointGravitySource(ourFrame, mu, roi));
        parent.cubePlanet(new CubePlanet(ourFrame, radius, angularCoordinates, constructDimensionData(), renderDataOverride, clouds));
    }

    protected PlanetDimensionData constructDimensionData() {
        if (linkedDimension == null) return null;
        return new PlanetDimensionData(linkedDimension, transferHeight);
    }

    public PlanetDefinitionBuilder setLinkedDimension(@Nullable ResourceKey<Level> linkedDimension) {
        this.linkedDimension = linkedDimension;
        return this;
    }

    public PlanetDefinitionBuilder setDimensionTransferHeight(int transferHeight) {
        this.transferHeight = transferHeight;
        return this;
    }

    public PlanetDefinitionBuilder setRenderDataOverride(@NotNull IntFunction<byte[]> override) {
        this.renderDataOverride = override;
        return this;
    }

    public PlanetDefinitionBuilder setClouds(boolean clouds) {
        this.clouds = clouds;
        return this;
    }

    /**
     * @param radius meters/blocks
     */
    public PlanetDefinitionBuilder setRadius(double radius) {
        this.radius = radius;
        return this;
    }

    public PlanetDefinitionBuilder setRotationAxis(Vector3d rotationAxis) {
        this.rotationAxis = rotationAxis;
        return this;
    }

    public PlanetDefinitionBuilder setTicksPerRevolution(int ticksPerRevolution) {
        this.ticksPerRevolution = ticksPerRevolution;
        return this;
    }

    public PlanetDefinitionBuilder setTidalLocked() {
        if (this.orbited == null) {
            throw new IllegalStateException("Builder does not have an orbit!");
        }
        tidalLock = true;
        return this;
    }

    public void setAngularCoordinates(TimeStampedAngularCoordinates angularCoordinates) {
        this.angularCoordinates = angularCoordinates;
    }

    public PlanetDefinitionBuilder setOrbit(@NotNull String gravitySourceName, @NotNull TimeStampedPVCoordinates orbitCoords) {
        return setOrbit(parent.getGravitySource(parent.getFrameByName(gravitySourceName), false), orbitCoords);
    }

    public PlanetDefinitionBuilder setOrbit(@NotNull PointGravitySource orbited, @NotNull TimeStampedPVCoordinates orbitCoords) {
        this.orbited = orbited;
        this.orbitCoords = orbitCoords;
        return this;
    }

    public PlanetDefinitionBuilder setCircularOrbit(@NotNull String gravitySourceName, @NotNull Vector3D position, @NotNull Vector3D orbitAxis) {
        return setCircularOrbit(parent.getGravitySource(parent.getFrameByName(gravitySourceName), false), position, orbitAxis);
    }

    public PlanetDefinitionBuilder setCircularOrbit(@NotNull PointGravitySource orbited, @NotNull Vector3D position, @NotNull Vector3D orbitAxis) {
        return this.setCircularOrbit(orbited, position, orbitAxis, DeepSpaceData.EPOCH);
    }

    public PlanetDefinitionBuilder setCircularOrbit(@NotNull String gravitySourceName, @NotNull Vector3D position, @NotNull Vector3D orbitAxis, @NotNull AbsoluteDate positionDate) {
        return setCircularOrbit(parent.getGravitySource(parent.getFrameByName(gravitySourceName), false), position, orbitAxis, positionDate);
    }

    public PlanetDefinitionBuilder setCircularOrbit(@NotNull PointGravitySource orbited, @NotNull Vector3D position, @NotNull Vector3D orbitAxis, @NotNull AbsoluteDate positionDate) {
        this.orbited = orbited;
        double velMagnitudeSquared = this.orbited.mu() / position.getNorm();
        Vector3D vel = orbitAxis.crossProduct(position);
        vel = vel.scalarMultiply(Math.sqrt(velMagnitudeSquared / vel.getNormSq()));
        this.orbitCoords = new TimeStampedPVCoordinates(positionDate, position, vel);
        return this;
    }

    public PlanetDefinitionBuilder setFixedPosition(@NotNull String referenceFrameName, @NotNull Vector3D position) {
        FrameTree frame = parent.getFrameByName(referenceFrameName);
        if (frame == null) throw new IllegalArgumentException("Frame could not be found!");
        return setFixedPosition(frame, position);
    }

    public PlanetDefinitionBuilder setFixedPosition(@NotNull FrameTree positionFrame, @NotNull Vector3D position) {
        this.fixedPositionParentFrame = positionFrame;
        this.fixedPosition = position;
        return this;
    }

    /**
     * @param accelerationAtSurface meters per second squared
     */
    public PlanetDefinitionBuilder setAccelerationAtSurface(double accelerationAtSurface) {
        this.accelerationAtSurface = accelerationAtSurface;
        return this;
    }

    /**
     * @param mu meters cubed per second squared
     */
    public PlanetDefinitionBuilder setMu(double mu) {
        this.mu = mu;
        return this;
    }

    public PlanetDefinitionBuilder setFrameName(String frameName) {
        this.frameName = frameName;
        return this;
    }
}
