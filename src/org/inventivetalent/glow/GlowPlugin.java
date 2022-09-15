package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.PacketListenerAPI;

import java.util.Random;

public class GlowPlugin extends JavaPlugin {

    static GlowPlugin instance;
    GlowAPI glowAPI;

    @Override
    public void onLoad() {
        instance = this;
        glowAPI = new GlowAPI(this);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(glowAPI, this);
        PacketListenerAPI.addPacketHandler(glowAPI);

        new MetricsLite(this, 2190);

        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void on(PlayerInteractEvent event) {
                GlowAPI.setGlowing(event.getPlayer(), GlowAPI.Color.values()[new Random().nextInt(GlowAPI.Color.values().length)], Bukkit.getOnlinePlayers());
            }

        }, this);
    }

    @Override
    public void onDisable() {
        PacketListenerAPI.removePacketHandler(glowAPI);
    }

}
