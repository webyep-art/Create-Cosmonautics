package dev.devce.rocketnautics.api.orbit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData;
import dev.devce.rocketnautics.content.orbit.universe.PlanetExtras;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeOffset;
import org.orekit.utils.AngularCoordinates;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedAngularCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.lang.Math;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

public class DeepSpaceHelper {
    public static final AbsoluteDate EPOCH = AbsoluteDate.ARBITRARY_EPOCH;
    private static final long ATTOS_IN_TICK = 50000000000000000L;
    public static final TimeOffset TICK = new TimeOffset(0L, ATTOS_IN_TICK);

    public static final Codec<Vector3D> VEC3D_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.DOUBLE.fieldOf("x").forGetter(Vector3D::getX),
                    Codec.DOUBLE.fieldOf("y").forGetter(Vector3D::getY),
                    Codec.DOUBLE.fieldOf("z").forGetter(Vector3D::getZ)
            ).apply(i, Vector3D::new)
    );
    public static final StreamCodec<ByteBuf, Vector3D> VEC3D_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                buf.writeDouble(obj.getX());
                buf.writeDouble(obj.getY());
                buf.writeDouble(obj.getZ());
            }, (buf) -> {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                return new Vector3D(x, y, z);
            }
    );

    public static final Codec<Rotation> ROTATION_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.DOUBLE.fieldOf("q0").forGetter(Rotation::getQ0),
                    Codec.DOUBLE.fieldOf("q1").forGetter(Rotation::getQ1),
                    Codec.DOUBLE.fieldOf("q2").forGetter(Rotation::getQ2),
                    Codec.DOUBLE.fieldOf("q3").forGetter(Rotation::getQ3)
            ).apply(i, (q0, q1, q2, q3) -> new Rotation(q0, q1, q2, q3, false))
    );

    public static final StreamCodec<ByteBuf, Rotation> ROTATION_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                buf.writeDouble(obj.getQ0());
                buf.writeDouble(obj.getQ1());
                buf.writeDouble(obj.getQ2());
                buf.writeDouble(obj.getQ3());
            }, (buf) -> {
                double q0 = buf.readDouble();
                double q1 = buf.readDouble();
                double q2 = buf.readDouble();
                double q3 = buf.readDouble();
                return new Rotation(q0, q1, q2, q3, false);
            }
    );

    public static final Codec<PVCoordinates> PVCOORDS_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    VEC3D_CODEC.fieldOf("r").forGetter(PVCoordinates::getPosition),
                    VEC3D_CODEC.fieldOf("v").forGetter(PVCoordinates::getVelocity),
                    VEC3D_CODEC.fieldOf("a").forGetter(PVCoordinates::getAcceleration)
            ).apply(i, PVCoordinates::new)
    );

    public static final StreamCodec<ByteBuf, PVCoordinates> PVCOORDS_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                VEC3D_CODEC_S.encode(buf, obj.getPosition());
                VEC3D_CODEC_S.encode(buf, obj.getVelocity());
                VEC3D_CODEC_S.encode(buf, obj.getAcceleration());
            }, (buf) -> {
                Vector3D r = VEC3D_CODEC_S.decode(buf);
                Vector3D v = VEC3D_CODEC_S.decode(buf);
                Vector3D a = VEC3D_CODEC_S.decode(buf);
                return new PVCoordinates(r, v, a);
            }
    );

    public static final Codec<AngularCoordinates> ANGULARCOORDS_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    ROTATION_CODEC.fieldOf("Θ").forGetter(AngularCoordinates::getRotation),
                    VEC3D_CODEC.fieldOf("ω").forGetter(AngularCoordinates::getRotationRate),
                    VEC3D_CODEC.fieldOf("α").forGetter(AngularCoordinates::getRotationAcceleration)
            ).apply(i, AngularCoordinates::new)
    );

    public static final StreamCodec<ByteBuf, AngularCoordinates> ANGULARCOORDS_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                ROTATION_CODEC_S.encode(buf, obj.getRotation());
                VEC3D_CODEC_S.encode(buf, obj.getRotationRate());
                VEC3D_CODEC_S.encode(buf, obj.getRotationAcceleration());
            }, (buf) -> {
                Rotation r = ROTATION_CODEC_S.decode(buf);
                Vector3D v = VEC3D_CODEC_S.decode(buf);
                Vector3D a = VEC3D_CODEC_S.decode(buf);
                return new AngularCoordinates(r, v, a);
            }
    );

    public static final Codec<TimeOffset> TIME_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.LONG.fieldOf("sec").forGetter(TimeOffset::getSeconds),
                    Codec.LONG.fieldOf("atto").forGetter(TimeOffset::getAttoSeconds)
            ).apply(i, TimeOffset::new)
    );

    public static final StreamCodec<ByteBuf, TimeOffset> TIME_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                buf.writeLong(obj.getSeconds());
                buf.writeLong(obj.getAttoSeconds());
            }, (buf) -> {
                long secs = buf.readLong();
                long attos = buf.readLong();
                return new TimeOffset(secs, attos);
            }
    );

    public static final Codec<AbsoluteDate> DATE_CODEC = TIME_CODEC.xmap(AbsoluteDate::new, UnaryOperator.identity());

    public static final StreamCodec<ByteBuf, AbsoluteDate> DATE_CODEC_S = TIME_CODEC_S.map(AbsoluteDate::new, UnaryOperator.identity());

    public static final Codec<TimeStampedPVCoordinates> STAMPED_PVCOORDS_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    DATE_CODEC.fieldOf("d").forGetter(TimeStampedPVCoordinates::getDate),
                    VEC3D_CODEC.fieldOf("r").forGetter(PVCoordinates::getPosition),
                    VEC3D_CODEC.fieldOf("v").forGetter(PVCoordinates::getVelocity),
                    VEC3D_CODEC.fieldOf("a").forGetter(PVCoordinates::getAcceleration)
            ).apply(i, TimeStampedPVCoordinates::new)
    );

    public static final StreamCodec<ByteBuf, TimeStampedPVCoordinates> STAMPED_PVCOORDS_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                DATE_CODEC_S.encode(buf, obj.getDate());
                VEC3D_CODEC_S.encode(buf, obj.getPosition());
                VEC3D_CODEC_S.encode(buf, obj.getVelocity());
                VEC3D_CODEC_S.encode(buf, obj.getAcceleration());
            }, (buf) -> {
                AbsoluteDate d = DATE_CODEC_S.decode(buf);
                Vector3D r = VEC3D_CODEC_S.decode(buf);
                Vector3D v = VEC3D_CODEC_S.decode(buf);
                Vector3D a = VEC3D_CODEC_S.decode(buf);
                return new TimeStampedPVCoordinates(d, r, v, a);
            }
    );

    public static final Codec<TimeStampedAngularCoordinates> STAMPED_ANGULARCOORDS_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    DATE_CODEC.fieldOf("d").forGetter(TimeStampedAngularCoordinates::getDate),
                    ROTATION_CODEC.fieldOf("Θ").forGetter(AngularCoordinates::getRotation),
                    VEC3D_CODEC.fieldOf("ω").forGetter(AngularCoordinates::getRotationRate),
                    VEC3D_CODEC.fieldOf("α").forGetter(AngularCoordinates::getRotationAcceleration)
            ).apply(i, TimeStampedAngularCoordinates::new)
    );

    public static final StreamCodec<ByteBuf, TimeStampedAngularCoordinates> STAMPED_ANGULARCOORDS_CODEC_S = StreamCodec.of(
            (buf, obj) -> {
                DATE_CODEC_S.encode(buf, obj.getDate());
                ROTATION_CODEC_S.encode(buf, obj.getRotation());
                VEC3D_CODEC_S.encode(buf, obj.getRotationRate());
                VEC3D_CODEC_S.encode(buf, obj.getRotationAcceleration());
            }, (buf) -> {
                AbsoluteDate d = DATE_CODEC_S.decode(buf);
                Rotation r = ROTATION_CODEC_S.decode(buf);
                Vector3D v = VEC3D_CODEC_S.decode(buf);
                Vector3D a = VEC3D_CODEC_S.decode(buf);
                return new TimeStampedAngularCoordinates(d, r, v, a);
            }
    );

    @Nullable
    public static <T> Tag write(@NotNull Codec<T> codec, T obj) {
        return write(codec, obj, null);
    }

    @Nullable
    @Contract("_,_,!null->!null;_,null,_->param3")
    public static <T> Tag write(@NotNull Codec<T> codec, T obj, Tag fallback) {
        if (obj == null) return fallback;
        DataResult<Tag> res = codec.encodeStart(NbtOps.INSTANCE, obj);
        return res.result().orElse(fallback);
    }

    @Nullable
    public static <T> T read(@NotNull Codec<T> codec, Tag tag) {
        return read(codec, tag, null);
    }

    @Nullable
    @Contract("_,_,!null->!null;_,null,_->param3")
    public static <T> T read(@NotNull Codec<T> codec, Tag tag, T fallback) {
        if (tag == null) return fallback;
        DataResult<T> res = codec.parse(NbtOps.INSTANCE, tag);
        return res.result().orElse(fallback);
    }
    public static Vector3d adapt(Vector3D vec) {
        return new Vector3d(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vector3D adapt(Vector3dc vec) {
        return new Vector3D(vec.x(), vec.y(), vec.z());
    }

    public static Vector3f adaptf(Vector3D vec) {
        return new Vector3f((float) vec.getX(), (float) vec.getY(), (float) vec.getZ());
    }

    public static Vector3D adaptf(Vector3f vec) {
        return new Vector3D(vec.x(), vec.y(), vec.z());
    }


    public static Quaterniond adapt(Rotation rot) {
        return new Quaterniond(-rot.getQ1(), -rot.getQ2(), -rot.getQ3(), rot.getQ0());
    }

    public static Rotation adapt(Quaterniondc rot) {
        return new Rotation(rot.w(), rot.x(), rot.y(), rot.z(), true);
    }

    public static Pair<TimeStampedPVCoordinates, Rotation> localPositionToGlobalPositionAndRotation(Vector3dc localPosition, @Nullable Vector3dc localVelocity, CubePlanet planet, AbsoluteDate date) {
        Rotation rotation = planet.getRotationAtTime(date);
        rotation = rotation.compose(new Rotation(Vector3D.PLUS_J, Vector3D.PLUS_K), RotationConvention.VECTOR_OPERATOR);
        Vector3d scaledPosition = localPosition.mul(planet.radius() / 30_000_000, new Vector3d());
        Vector3D unrotatedPosition = new Vector3D(scaledPosition.x(), localPosition.y() + planet.radius(), scaledPosition.z());
        if (localVelocity != null && localVelocity.lengthSquared() < 1e-5) {
            localVelocity = new Vector3d(0, 1, 0);
        }
        Vector3D actualPosition = rotation.applyTo(unrotatedPosition);
        Vector3D actualVelocity = Vector3D.ZERO;
        if (localVelocity != null) {
            actualVelocity = rotation.applyTo(adapt(localVelocity))
                    .add(actualPosition.crossProduct(planet.rotationDescription().getRotationRate())); // compensate for rotation rate of the planet
        }
        return Pair.of(new TimeStampedPVCoordinates(date, actualPosition, actualVelocity), rotation);
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean isDeepSpace() {
        return Minecraft.getInstance().level.dimension() == RocketDimensions.DEEP_SPACE;
    }

    public static boolean isDeepSpace(Level level) {
        return level.dimension() == RocketDimensions.DEEP_SPACE;
    }

    public static boolean isDeepSpace(ResourceKey<Level> key) {
        return key == RocketDimensions.DEEP_SPACE;
    }

    public static AbsoluteDate getDateByTicks(long ticks) {
        if (ticks < 0) {
            return EPOCH.shiftedBy(TICK.negate().multiply(-ticks));
        }
        return EPOCH.shiftedBy(TICK.multiply(ticks));
    }

    public static Optional<UniverseDefinition> getUniverse(@NotNull Level level) {
        if (level.getServer() != null) {
            return Optional.of(DeepSpaceData.getInstance(level.getServer()).getUniverse());
        } else {
            return Optional.ofNullable(DeepSpaceHandler.getUniverse());
        }
    }

    public static Optional<PlanetDimensionData> getDataForDimension(@NotNull Level level) {
        return getUniverse(level).map(u -> u.getPlanetByDimension(level.dimension()))
                .map(CubePlanet::linkedDimension);
    }

    public static Optional<PlanetExtras> getExtrasForDimension(@NotNull Level level) {
        return getUniverse(level).map(u -> u.getPlanetByDimension(level.dimension()))
                .map(CubePlanet::extras);
    }

    public static boolean shouldOverrideLevelTime(@NotNull Level level) {
        return getDataForDimension(level).map(PlanetDimensionData::controlsDimensionDayTime).orElse(false);
    }

    public static void checkAndOverrideLevelTime(UniverseDefinition universe, AbsoluteDate date, Level level, LongConsumer overrider) {
        // note: minecraft sun rises in +x and sets in -x
        CubePlanet planet = universe.getPlanetByDimension(level.dimension());
        if (planet != null && planet.linkedDimension() != null && planet.linkedDimension().controlsDimensionDayTime()) {
            // TODO consider eclipses?
            Optional<Vector3D> skyPos = computeSkyPos(universe, date, planet);
            if (skyPos.isEmpty()) return;
            // look a minute into the future to determine if we are rising or setting.
            Optional<Vector3D> futureSkyPos = computeSkyPos(universe, date.shiftedBy(60), planet);
            if (futureSkyPos.isEmpty()) return;
            double currentHeightRating = skyPos.get().normalize().dotProduct(Vector3D.PLUS_J);
            double futureHeightRating = futureSkyPos.get().normalize().dotProduct(Vector3D.PLUS_J);
            double angleOffVertical = Math.acos(currentHeightRating);
            long timeFrom6000 = (long) (12000 * angleOffVertical / Math.PI);
            if (futureHeightRating > currentHeightRating) {
                // rising, time from 18000 to 6000 or equivalently 18000 to 30000
                overrider.accept(30000 - timeFrom6000);
            } else {
                // setting, time from 6000 to 18000
                overrider.accept(6000 + timeFrom6000);
            }
        }
    }

    private static Optional<Vector3D> computeSkyPos(UniverseDefinition universe, AbsoluteDate date, CubePlanet planet) {
        return universe.getFrameByID(planet.linkedDimension().controlDimensionDayTimeID()).map(sourceFrame -> {
            try {
                return sourceFrame.getStaticTransformTo(planet.orekitFrame(), date).transformPosition(Vector3D.ZERO);
            } catch (Exception e) {
                return null;
            }
        }).map(v -> {
            // rotate the source into the canonical side
            Rotation rotation = planet.getRotationAtTime(date);
            rotation = rotation.compose(new Rotation(Vector3D.PLUS_J, Vector3D.PLUS_K), RotationConvention.VECTOR_OPERATOR);
            return rotation.applyInverseTo(v);
        });
    }
}
