package org.inventivetalent.glowapi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlowData {

	//Maps player-UUID to Color
	public Map<UUID, GlowAPI.Color> colorMap = new HashMap<>();

	@Override
	public boolean equals(Object o) {
		if (this == o) { return true; }
		if (o == null || getClass() != o.getClass()) { return false; }

		GlowData glowData = (GlowData) o;

		return colorMap != null ? colorMap.equals(glowData.colorMap) : glowData.colorMap == null;

	}

	@Override
	public int hashCode() {
		return colorMap != null ? colorMap.hashCode() : 0;
	}
}
