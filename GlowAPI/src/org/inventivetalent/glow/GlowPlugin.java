package org.inventivetalent.glow;

import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.apihelper.APIManager;
import org.mcstats.MetricsLite;

public class GlowPlugin extends JavaPlugin {

	static GlowPlugin instance;
	GlowAPI glowAPI = new GlowAPI();

	@Override
	public void onLoad() {
		instance = this;
		//Register this API if the plugin got loaded
		APIManager.registerAPI(glowAPI, this);
	}

	@Override
	public void onEnable() {
		try {
			MetricsLite metrics = new MetricsLite(this);
			if (metrics.start()) {
				getLogger().info("Metrics started");
			}
		} catch (Exception e) {
		}

		//Initialize this API of the plugin got loaded
		APIManager.initAPI(GlowAPI.class);
	}

}
