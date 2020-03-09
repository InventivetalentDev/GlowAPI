package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.glow.listeners.PlayerJoinListener;
import org.inventivetalent.glow.listeners.PlayerQuitListener;

public class GlowPlugin extends JavaPlugin {

	GlowAPI glowAPI = new GlowAPI();

	public static GlowPlugin getPlugin() {
		return getPlugin(GlowPlugin.class);
	}

	@Override
	public void onEnable() {
		new MetricsLite(this, 2190);
		Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
		Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
		glowAPI.init(this);
	}

}
