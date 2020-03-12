package org.inventivetalent.glow;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlowData {

	//Maps player-UUID to Color
	public final Map<UUID, GlowAPI.Color> colorMap = new ConcurrentHashMap<>();

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		GlowData glowData = (GlowData) o;

		return Objects.equals(colorMap, glowData.colorMap);
	}

	@Override
	public int hashCode() {
		return colorMap.hashCode();
	}
}
