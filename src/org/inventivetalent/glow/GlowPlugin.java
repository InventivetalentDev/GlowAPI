package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.PacketListenerAPI;
import org.inventivetalent.reflection.minecraft.Minecraft;

public class GlowPlugin extends JavaPlugin {

	static GlowPlugin instance;
	GlowAPI glowAPI = new GlowAPI();

	@Override
	public void onLoad() {
		instance = this;
	}

	@Override
	public void onEnable() {
		glowAPI.init(this);
		if (Minecraft.MINECRAFT_VERSION.newerThan(Minecraft.Version.v1_19_R1)) {
			PacketListenerAPI.addPacketHandler(glowAPI);
		}

		new MetricsLite(this, 2190);
	}

	@Override
	public void onDisable() {
		if (Minecraft.MINECRAFT_VERSION.newerThan(Minecraft.Version.v1_19_R1)) {
			PacketListenerAPI.removePacketHandler(glowAPI);
		}
	}

}
