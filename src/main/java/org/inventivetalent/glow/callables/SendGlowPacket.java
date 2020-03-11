package org.inventivetalent.glow.callables;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Serializer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerEntityMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class SendGlowPacket implements Callable<Void> {

    private static final Serializer byteSerializer = Registry.get(Byte.class);

    Entity entity;
    boolean glowing;
    Player player;

    public SendGlowPacket(@NotNull Entity entity,
                          boolean glowing,
                          @NotNull Player player) {
        this.entity = entity;
        this.glowing = glowing;
        this.player = player;
    }

    @Nullable
    @Override
    public Void call() throws Exception {
        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        final WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);
        final WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcherObject(0, byteSerializer);

        final int invertedEntityId = -entity.getEntityId();

        final WrappedDataWatcher dataWatcher = WrappedDataWatcher.getEntityWatcher(entity);
        final List<WrappedWatchableObject> dataWatcherObjects = dataWatcher.getWatchableObjects();

        byte entityByte = 0x00;
        if (!dataWatcherObjects.isEmpty()) entityByte = (byte) dataWatcherObjects.get(0).getValue();
        if (glowing) entityByte = (byte) (entityByte | GlowAPI.ENTITY_GLOWING_EFFECT);
        else entityByte = (byte) (entityByte & ~GlowAPI.ENTITY_GLOWING_EFFECT);

        final WrappedWatchableObject wrappedMetadata = new WrappedWatchableObject(dataWatcherObject, entityByte);
        final List<WrappedWatchableObject> metadata = Collections.singletonList(wrappedMetadata);

        wrappedPacket.setEntityID(invertedEntityId);
        wrappedPacket.setMetadata(metadata);

        try {
            GlowAPI.getPlugin().getProtocolManager().sendServerPacket(player, packet);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to send entity metadata packet to player " + player.toString(), e);
        }
        return null;
    }

}
