package org.inventivetalent.glow;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.PacketListenerAPI;

import java.io.File;
import java.io.IOException;

public class GlowPlugin extends JavaPlugin {

    static GlowPlugin instance;
    GlowAPI glowAPI;

    @Override
    public void onLoad() {
        instance = this;
        glowAPI = new GlowAPI(this);
        loadDefaults();
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

    private void loadDefaults() {
        FileConfiguration configuration;
        File dataFolder = getDataFolder();

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();

            // Fill the configuration with current values
            configuration = getConfig();
            configuration.set("nameTagVisibility", GlowAPI.TEAM_TAG_VISIBILITY);
            configuration.set("collision", GlowAPI.TEAM_PUSH);

            try {
                configuration.save(new File(dataFolder, "config.yml"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to save default config", e);
            }
        } else {
            configuration = getConfig();
        }

        String visibility = configuration.getString("nameTagVisibility");
        if (visibility != null) {
            if (!visibility.equals("always")
                    && !visibility.equals("hideForOtherTeams")
                    && !visibility.equals("hideForOwnTeam")
                    && !visibility.equals("never")) {
                getLogger().warning("Ignored unknown nameTagVisibility default value: '" + visibility + "'");
            } else {
                GlowAPI.TEAM_TAG_VISIBILITY = visibility;
            }
        }

        String push = configuration.getString("collision");
        if (push != null) {
            if (!push.equals("always")
                    && !push.equals("pushOtherTeams")
                    && !push.equals("pushOwnTeam")
                    && !push.equals("never")) {
                getLogger().warning("Ignored unknown collision default value: '" + push + "'");
            } else {
                GlowAPI.TEAM_PUSH = push;
            }
        }
    }
}
