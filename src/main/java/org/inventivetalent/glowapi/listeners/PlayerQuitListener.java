package org.inventivetalent.glowapi.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.inventivetalent.glowapi.GlowAPI;

public class PlayerQuitListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (GlowAPI.isGlowing(event.getPlayer(), receiver)) {
                GlowAPI.setGlowing(event.getPlayer(), null, receiver);
            }
        }
    }

}
