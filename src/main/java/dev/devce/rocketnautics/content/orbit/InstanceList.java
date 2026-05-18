package dev.devce.rocketnautics.content.orbit;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class InstanceList {
    private final int chunkPowerSize;
    private final Int2ObjectAVLTreeMap<DeepSpaceInstance> instances;

    private IntHeapPriorityQueue freedIDs = new IntHeapPriorityQueue();

    public InstanceList(int chunkPowerSize) {
        this.chunkPowerSize = chunkPowerSize;
        instances = new Int2ObjectAVLTreeMap<>();
    }

    public InstanceList(DeepSpaceData manager, CompoundTag tag) {
        this.chunkPowerSize = tag.getInt("PowerSize");
        instances = new Int2ObjectAVLTreeMap<>();
        ListTag instances = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instances.size(); i++) {
            DeepSpaceInstance instance = new DeepSpaceInstance(manager, instances.getCompound(i));
            this.instances.put(DeepSpaceData.unpackIdWithinSize(instance.getId()), instance);
        }
        int[] freed = tag.getIntArray("Freed");
        this.freedIDs = new IntHeapPriorityQueue(freed);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        ListTag instances = new ListTag(this.instances.size());
        this.instances.values().forEach(i -> instances.add(i.write()));
        int[] arr = new int[this.freedIDs.size()];
        int index = 0;
        while (!this.freedIDs.isEmpty()) {
            arr[index] = this.freedIDs.dequeueInt();
            index++;
        }
        this.freedIDs = new IntHeapPriorityQueue(arr);
        tag.put("Instances", instances);
        tag.putIntArray("Freed", arr);
        tag.putInt("PowerSize", chunkPowerSize);
        return tag;
    }

    public void tick(MinecraftServer server) {
        instances.values().forEach(i -> i.tick(server));
    }

    public int getChunkPowerSize() {
        return chunkPowerSize;
    }

    public DeepSpaceInstance createInstance(DeepSpaceData manager) {
        int id = freedIDs.isEmpty() ? instances.size() : freedIDs.dequeueInt();
        int[] corners = DeepSpaceData.getCornerXCornerZForParameters(chunkPowerSize, id);
        long packedID = DeepSpaceData.pack(chunkPowerSize, id);
        DeepSpaceInstance constructed = new DeepSpaceInstance(manager, 16 * (2 << chunkPowerSize), corners[0], corners[1], packedID);
        instances.put(id, constructed);
        return constructed;
    }

    @Nullable
    public DeepSpaceInstance getInstance(int id) {
        return instances.get(id);
    }

    @Nullable
    public DeepSpaceInstance retireInstance(int id) {
        DeepSpaceInstance instance = instances.remove(id);
        if (instance != null) {
            freedIDs.enqueue(id);
        }
        return instance;
    }
}
