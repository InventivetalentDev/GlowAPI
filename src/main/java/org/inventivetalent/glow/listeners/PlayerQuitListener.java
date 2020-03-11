package org.inventivetalent.glow.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.inventivetalent.glow.GlowAPI;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

public class PlayerQuitListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        GlowAPI.setGlowing(quittingPlayer, null, Bukkit.getOnlinePlayers()
            .parallelStream()
            .filter(player -> GlowAPI.isGlowing(quittingPlayer, player))
            .collect(Collectors.<Player>toList()));
    }

}
