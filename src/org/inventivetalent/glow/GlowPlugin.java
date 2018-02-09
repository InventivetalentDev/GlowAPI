package org.inventivetalent.glow;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.apihelper.APIManager;
import org.bstats.bukkit.MetricsLite;

import java.util.Random;

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
		new MetricsLite(this);

		//Initialize this API of the plugin got loaded
		APIManager.initAPI(GlowAPI.class);

		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void on(PlayerInteractEvent event) {
				GlowAPI.setGlowing(event.getPlayer(), GlowAPI.Color.values()[new Random().nextInt(GlowAPI.Color.values().length-1)], Bukkit.getOnlinePlayers());
			}
		}, this);
	}

}
