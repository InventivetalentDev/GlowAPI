package org.inventivetalent.glow.callables;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;
import org.inventivetalent.glow.GlowData;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class SetGlowing implements Callable<Void> {

    Entity entity;
    GlowAPI.Color color;
    NameTagVisibility nameTagVisibility;
    TeamPush teamPush;
    Player player;

    public SetGlowing(@Nullable Entity entity,
                      @Nullable GlowAPI.Color color,
                      @NotNull NameTagVisibility nameTagVisibility,
                      @NotNull TeamPush teamPush,
                      @Nullable Player player) {
        this.entity = entity;
        this.color = color;
        this.nameTagVisibility = nameTagVisibility;
        this.teamPush = teamPush;
        this.player = player;
    }

    @Override
    public Void call() throws Exception {
        if (player == null) return null;

        boolean glowing = color != null;
        if (entity == null) glowing = false;
        if (entity instanceof OfflinePlayer) {
            if (!((OfflinePlayer) entity).isOnline()) glowing = false;
        }

        final UUID entityUniqueId = (entity == null) ? null : entity.getUniqueId();
        final Map<UUID, GlowData> dataMap = GlowAPI.getDataMap();
        final boolean wasGlowing = dataMap.containsKey(entityUniqueId);
        final GlowData glowData = (wasGlowing && entity != null) ? dataMap.get(entityUniqueId) : new GlowData();
        final UUID playerUniqueId = player.getUniqueId();
        final GlowAPI.Color oldColor = wasGlowing ? glowData.colorMap.get(playerUniqueId) : null;

        if (glowing) glowData.colorMap.put(playerUniqueId, color);
        else glowData.colorMap.remove(playerUniqueId);

        if (glowData.colorMap.isEmpty()) dataMap.remove(entityUniqueId);
        else if (entity != null) dataMap.put(entityUniqueId, glowData);

        if (color != null && oldColor == color) return null;
        if (entity == null) return null;
        if (entity instanceof OfflinePlayer) {
            if (!((OfflinePlayer) entity).isOnline()) return null;
        }
        if (!player.isOnline()) return null;

        GlowAPI.sendGlowPacket(entity, glowing, player);

        final boolean createNewTeam = false;
        if (oldColor != null) {
            //We never add to NONE, so no need to remove
            if (oldColor != GlowAPI.Color.NONE) {
                final boolean addEntity = false;
                //use the old color to remove the player from its team
                GlowAPI.sendTeamPacket(entity, oldColor, createNewTeam, addEntity, nameTagVisibility, teamPush, player);
            }
        }
        if (glowing) {
            final boolean addEntity = color != GlowAPI.Color.NONE;
            GlowAPI.sendTeamPacket(entity, color, createNewTeam, addEntity, nameTagVisibility, teamPush, player);
        }
        return null;
    }

}
