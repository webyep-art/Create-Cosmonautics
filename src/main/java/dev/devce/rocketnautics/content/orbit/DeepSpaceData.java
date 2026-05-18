package dev.devce.rocketnautics.content.orbit;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.orbit.universe.StandardUniverseProvider;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.network.UniverseDefinitionPayload;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeOffset;
import org.orekit.utils.TimeStampedPVCoordinates;

@EventBusSubscriber(modid = RocketNautics.MODID)
public class DeepSpaceData extends SavedData {
    public static final ResourceKey<Level> DEEP_SPACE_DIM = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "deep_space"));
    public static final int LOGICAL_INSTANCE_HEIGHT = 1000;

    public static final String ID = "cosmonautics_deep_space_data";

    public static final AbsoluteDate EPOCH = AbsoluteDate.ARBITRARY_EPOCH;
    private static final long ATTOS_IN_TICK = 50000000000000000L;
    public static final TimeOffset TICK = new TimeOffset(0L, ATTOS_IN_TICK);

    public static DeepSpaceData getInstance(MinecraftServer server) {
        ServerLevel deepSpace = server.getLevel(DEEP_SPACE_DIM);
        DeepSpaceData data = deepSpace.getChunkSource().getDataStorage().computeIfAbsent(new Factory<>(DeepSpaceData::new, DeepSpaceData::load, null), ID);
        return data;
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean isDeepSpace() {
        return Minecraft.getInstance().level.dimension() == DEEP_SPACE_DIM;
    }

    public static boolean isDeepSpace(Level level) {
        return level.dimension() == DEEP_SPACE_DIM;
    }

    public static boolean isDeepSpace(ResourceKey<Level> key) {
        return key == DEEP_SPACE_DIM;
    }

    @SubscribeEvent
    public static void advanceUniverse(ServerTickEvent.Post event) {
        getInstance(event.getServer()).tick(event.getServer());
    }

    @SubscribeEvent
    public static void syncUniverse(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer splayer)) return;
        PacketDistributor.sendToPlayer(splayer, new UniverseDefinitionPayload(getInstance(splayer.server).getUniverse()));
    }

    // end static //

    private final UniverseDefinition universe = StandardUniverseProvider.createSunOverworldMoon().build();

    private final Int2ObjectMap<InstanceList> instances = new Int2ObjectOpenHashMap<>();

    private long universeTicks;
    private int nextFreeID = 0;

    public void tick(MinecraftServer server) {
        universeTicks += 1;
        instances.values().forEach(i -> i.tick(server));
        setDirty();
        if (instances.isEmpty()) debugInstance();
    }

    private void debugInstance() {
        // execute in rocketnautics:deep_space run tp Dev 48 1016 16
        DeepSpaceInstance instance = claimNewInstance(2);
        instance.getPosition().init(universe, "overworld", new TimeStampedPVCoordinates(EPOCH, new Vector3D(0, 0, 9_000_000D), new Vector3D(0, 3_300, 0)));
    }

    public DeepSpaceInstance claimNewInstance(int chunkSize) {
        setDirty();
        int powerSize = (int) Math.ceil(Math.log(chunkSize) / Math.log(2)) - 1;
        return instances.computeIfAbsent(powerSize, InstanceList::new).createInstance(this);
    }

    @Nullable
    public DeepSpaceInstance getInstanceForPos(int xPos, int zPos) {
        int[] params = getChunkPowerSizeIdWithinSizeForParameters(xPos, zPos);
        return getInstance(params[0], params[1]);
    }

    @Nullable
    public DeepSpaceInstance getInstance(long id) {
        return getInstance(unpackSize(id), unpackIdWithinSize(id));
    }

    @Nullable
    public DeepSpaceInstance getInstance(int chunkPowerSize, int idWithinSize) {
        InstanceList l = instances.get(chunkPowerSize);
        if (l == null) return null;
        return l.getInstance(idWithinSize);
    }

    @Nullable
    public DeepSpaceInstance retireInstance(long id) {
        return retireInstance(unpackSize(id), unpackIdWithinSize(id));
    }

    @Nullable
    public DeepSpaceInstance retireInstance(int chunkPowerSize, int idWithinSize) {
        InstanceList l = instances.get(chunkPowerSize);
        if (l == null) return null;
        setDirty();
        return l.retireInstance(idWithinSize);
    }

    public static long pack(int chunkPowSize, int idWithinSize) {
        return ((long) chunkPowSize << 32) + (idWithinSize & 0xFFFFFFFFL);
    }

    public static int unpackSize(long id) {
        return (int) (id >> 32);
    }

    public static int unpackIdWithinSize(long id) {
        return (int) (id & 0xFFFFFFFFL);
    }

    public UniverseDefinition getUniverse() {
        return universe;
    }

    public long getUniverseTicks() {
        return universeTicks;
    }

    public AbsoluteDate getUniverseTime() {
        return EPOCH.shiftedBy(TICK.multiply(universeTicks));
    }

    public static AbsoluteDate getTime(long ticks) {
        if (ticks < 0) {
            return EPOCH.shiftedBy(TICK.negate().multiply(-ticks));
        }
        return EPOCH.shiftedBy(TICK.multiply(ticks));
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        compoundTag.putLong("UniverseTicks", universeTicks);
        compoundTag.putInt("NextID", nextFreeID);
        ListTag list = new ListTag();
        for (InstanceList instanceList : instances.values()) {
            list.add(instanceList.write());
        }
        compoundTag.put("InstanceLists", list);
        return compoundTag;
    }

    private static DeepSpaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeepSpaceData data = new DeepSpaceData();
        data.universeTicks = tag.getLong("UniverseTicks");
        data.nextFreeID = tag.getInt("NextID");
        ListTag list = tag.getList("InstanceLists", ListTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            InstanceList instance = new InstanceList(data, list.getCompound(i));
            data.instances.put(instance.getChunkPowerSize(), instance);
        }
        return data;
    }

    // TODO mixin to CollisionGetter#borderCollision and Entity#collectColliders to add this
    // collision box at the same place the world border's collision box is added.
    public static VoxelShape getColliderForPosition(Vec3 position) {
        // compute the instance we are in
        int[] sizeAndId = getChunkPowerSizeIdWithinSizeForParameters((int) position.x, (int) position.z);
        int[] corners = getCornerXCornerZForParameters(sizeAndId[0], sizeAndId[1]);
        int blockSize = 16 * (2 << sizeAndId[0]);
        // subtract the instance bounds from the infinity box
        return Shapes.join(
                Shapes.INFINITY,
                Shapes.box(
                        corners[0],
                        LOGICAL_INSTANCE_HEIGHT,
                        corners[1],
                        corners[0] + blockSize + 1,
                        LOGICAL_INSTANCE_HEIGHT + blockSize + 1,
                        corners[1] + blockSize + 1
                ),
                BooleanOp.ONLY_FIRST
        );
    }

    public static int[] getChunkPowerSizeIdWithinSizeForParameters(int negX, int negZ) {
        if (negX < 0 || negZ < 0) return new int[] { 1, 0 };
        // convert to chunkpos
        negX /= 16;
        negZ /= 16;
        // derive chunk size from X position
        // since the power term dominates at large scale, get a definite upper bound
        int chunkPowerSize = Math.max((int) (Math.log(negX * 1.1) / Math.log(2)) + 1, 1);
        // descend until we are below or equal to the target; at large scales, we will need to do this once.
        int size = chunkPowerSize * 16 + (2 << chunkPowerSize);
        while (size > negX && chunkPowerSize > 0) {
            chunkPowerSize--;
            size = chunkPowerSize * 16 + (2 << chunkPowerSize);
        }
        // derive id from Z position and chunk size
        return new int[] { chunkPowerSize, negZ / (16 + size) };
    }

    public static int[] getCornerXCornerZForParameters(int chunkPowerSize, int idWithinSize) {
        int chunkSize = 2 << chunkPowerSize;
        return new int[] { 16 * (chunkPowerSize * 16 + chunkSize), 16 * (idWithinSize * (16 + chunkSize)) };
    }
}
