package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.plugin.java.JavaPlugin;

public class GlowPlugin extends JavaPlugin {

	GlowAPI glowAPI = new GlowAPI();

	public static GlowPlugin getPlugin() {
		return getPlugin(GlowPlugin.class);
	}

	@Override
	public void onEnable() {
		new MetricsLite(this, 2190);
		glowAPI.init(this);
	}

}
