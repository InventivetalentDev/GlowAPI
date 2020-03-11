package org.inventivetalent.glow.callables;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.Modes;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.Callable;

public class SendTeamPacket implements Callable<Void> {

    Entity entity;
    GlowAPI.Color color;
    boolean createNewTeam;
    boolean addEntity;
    NameTagVisibility nameTagVisibility;
    TeamPush teamPush;
    Player player;

    public SendTeamPacket(@Nullable Entity entity,
                          @NotNull GlowAPI.Color color,
                          boolean createNewTeam,
                          boolean addEntity,
                          @NotNull NameTagVisibility nameTagVisibility,
                          @NotNull TeamPush teamPush,
                          @NotNull Player player) {
        this.entity = entity;
        this.color = color;
        this.createNewTeam = createNewTeam;
        this.addEntity = addEntity;
        this.nameTagVisibility = nameTagVisibility;
        this.teamPush = teamPush;
        this.player = player;
    }

    @Override
    public Void call() throws Exception {
        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
        final WrapperPlayServerScoreboardTeam wrappedPacket = new WrapperPlayServerScoreboardTeam(packet);

        Modes packetMode;
        if (createNewTeam) packetMode = Modes.TEAM_CREATED;
        else if (addEntity) packetMode = Modes.PLAYERS_ADDED;
        else packetMode = Modes.PLAYERS_REMOVED;

        final String teamName = color.getTeamName();

        wrappedPacket.setPacketMode(packetMode);
        wrappedPacket.setName(teamName);
        wrappedPacket.setNameTagVisibility(nameTagVisibility);
        wrappedPacket.setTeamPush(teamPush);

        if (createNewTeam) {
            wrappedPacket.setTeamColor(color.getChatColor());
            wrappedPacket.setTeamPrefix(color.getChatColor().toString());
            wrappedPacket.setTeamDisplayName(teamName);
            wrappedPacket.setTeamSuffix("");
            wrappedPacket.setAllowFriendlyFire(true);
            wrappedPacket.setCanSeeFriendlyInvisibles(false);
        } else {
            if (entity == null) return null;
            //Add/remove entries
            String entry;
            if (entity instanceof OfflinePlayer) {
                //Players still use the name...
                entry = entity.getName();
            } else {
                entry = entity.getUniqueId().toString();
            }
            Collection<String> entries = wrappedPacket.getEntries();
            entries.add(entry);
        }

        try {
            GlowAPI.getPlugin().getProtocolManager().sendServerPacket(player, packet);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to send team packet to player " + player.toString(), e);
        }
        return null;
    }

}
