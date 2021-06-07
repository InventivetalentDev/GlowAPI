package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.plugin.java.JavaPlugin;

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

		new MetricsLite(this, 2190);
	}

}
