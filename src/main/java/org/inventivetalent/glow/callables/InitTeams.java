package org.inventivetalent.glow.callables;

import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class InitTeams implements Callable<Void> {

    Player player;
    NameTagVisibility nameTagVisibility;
    TeamPush teamPush;

    public InitTeams(@NotNull Player player,
                     @NotNull NameTagVisibility nameTagVisibility,
                     @NotNull TeamPush teamPush) {
        this.player = player;
        this.nameTagVisibility = nameTagVisibility;
        this.teamPush = teamPush;
    }

    @Nullable
    @Override
    public Void call() throws Exception {
        Arrays.stream(GlowAPI.Color.values())
            .parallel()
            .map(color -> GlowAPI.sendTeamPacketAsync(null, color, true, false, nameTagVisibility, teamPush, player))
            .forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        return null;
    }

}
