package org.inventivetalent.glow.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.inventivetalent.glow.GlowAPI;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        GlowAPI.initTeam(event.getPlayer());
    }

}
