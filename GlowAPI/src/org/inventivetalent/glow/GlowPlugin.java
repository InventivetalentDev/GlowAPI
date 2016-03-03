package org.inventivetalent.glow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GlowPlugin extends JavaPlugin implements Listener {

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
	}

	@EventHandler
	public void onJoin(final PlayerJoinEvent event) {
		//Initialize the teams
		GlowAPI.initTeam(event.getPlayer());
	}

	@EventHandler
	public void onQuit(final PlayerQuitEvent event) {
		for (Player receiver : Bukkit.getOnlinePlayers()) {
			if (GlowAPI.isGlowing(event.getPlayer(), receiver)) {
				GlowAPI.setGlowing(event.getPlayer(), null, receiver);
			}
		}
	}

}
