package org.inventivetalent.glow.callables;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class SetGlowingMany implements Callable<Void> {

    Collection<? extends Entity> entities;
    GlowAPI.Color color;
    Player player;

    public SetGlowingMany(@NotNull Collection<? extends Entity> entities,
                          @Nullable GlowAPI.Color color,
                          @NotNull Player player) {
        this.entities = entities;
        this.color = color;
        this.player = player;
    }

    @Nullable
    @Override
    public Void call() throws Exception {
        entities
            .parallelStream()
            .map(entity -> GlowAPI.setGlowingAsync(entity, color, player))
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
