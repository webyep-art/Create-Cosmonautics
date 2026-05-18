package dev.devce.rocketnautics.api.orbit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import org.hipparchus.geometry.euclidean.threed.Rotation;
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

import java.util.function.UnaryOperator;

public class DeepSpaceHelper {
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
        return new Quaterniond(rot.getQ1(), rot.getQ2(), rot.getQ3(), rot.getQ0());
    }

    public static Rotation adapt(Quaterniondc rot) {
        return new Rotation(rot.w(), rot.x(), rot.y(), rot.z(), true);
    }
}
