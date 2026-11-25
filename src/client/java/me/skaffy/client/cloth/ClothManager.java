package me.skaffy.client.cloth;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ClothManager {
	private static final class Entry {
		final ClothGrid grid;
		long lastTick;

		Entry(ClothGrid grid, long tick) {
			this.grid = grid;
			this.lastTick = tick;
		}
	}

	private static final Long2ObjectOpenHashMap<Entry> BANNERS = new Long2ObjectOpenHashMap<>();
	private static final Map<UUID, Entry> CAPES = new HashMap<>();

	private ClothManager() {
	}

	public static ClothGrid banner(ClientLevel level, BlockPos pos, Vec3 topLeft, Vec3 topRight, Vec3 outwardNormal) {
		long key = pos.asLong();
		long now = level.getGameTime();
		Entry entry = BANNERS.get(key);
		if (entry == null) {
			entry = new Entry(new ClothGrid(5, 9, 0.875F, 1.75F), now - 1L);
			BANNERS.put(key, entry);
		}
		if (entry.lastTick != now) {
			entry.lastTick = now;
			entry.grid.step(level, topLeft, topRight, outwardNormal, 1.0F, 1.0F);
		}
		return entry.grid;
	}

	public static ClothGrid cape(ClientLevel level, UUID uuid, Vec3 topLeft, Vec3 topRight, Vec3 outwardNormal) {
		long now = level.getGameTime();
		Entry entry = CAPES.get(uuid);
		if (entry == null) {
			entry = new Entry(new ClothGrid(5, 9, 0.625F, 1.0F), now - 1L);
			CAPES.put(uuid, entry);
		}
		if (entry.lastTick != now) {
			entry.lastTick = now;
			entry.grid.step(level, topLeft, topRight, outwardNormal, 1.15F, 1.0F);
		}
		return entry.grid;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			BANNERS.clear();
			CAPES.clear();
			return;
		}
		long now = level.getGameTime();
		BANNERS.values().removeIf(e -> now - e.lastTick > 40L);
		CAPES.values().removeIf(e -> now - e.lastTick > 40L);
	}
}
