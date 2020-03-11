package org.inventivetalent.glow.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.inventivetalent.glow.GlowAPI;
import org.jetbrains.annotations.NotNull;

public class PlayerQuitListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        Bukkit.getOnlinePlayers()
            .parallelStream()
            .filter(player -> GlowAPI.isGlowing(quittingPlayer, player))
            .forEach(player -> GlowAPI.setGlowing(quittingPlayer, null, player));
    }

}
