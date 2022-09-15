package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.PacketListenerAPI;

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
    }

    @Override
    public void onDisable() {
        PacketListenerAPI.removePacketHandler(glowAPI);
    }

}
