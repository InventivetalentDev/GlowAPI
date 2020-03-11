package org.inventivetalent.glow.callables;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

public class EntityById implements Callable<Entity> {

    private int entityId;
    private World world;

    public EntityById(@NotNull World world, int entityId) {
        this.entityId = entityId;
        this.world = world;
    }

    @Nullable
    @Override
    public Entity call() {
        return world
            .getEntities()
            .parallelStream()
            .filter(entity -> entity.getEntityId() == entityId)
            .findAny()
            .orElse(null);
    }

}
