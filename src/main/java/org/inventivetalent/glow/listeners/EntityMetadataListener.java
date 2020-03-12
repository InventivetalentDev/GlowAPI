package org.inventivetalent.glow.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.inventivetalent.glow.GlowAPI;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerEntityMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EntityMetadataListener implements PacketListener {

    @Override
    public void onPacketSending(@NotNull PacketEvent packetEvent) {
        PacketContainer packet = packetEvent.getPacket();
        WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);

        final int entityId = wrappedPacket.getEntityID();
        if (entityId < 0) {//Our packet
            //Reset the ID and let it through
            final int invertedEntityId = -entityId;
            wrappedPacket.setEntityID(invertedEntityId);
            return;
        }

        final List<WrappedWatchableObject> metaData = wrappedPacket.getMetadata();
        if (metaData == null || metaData.isEmpty()) return;//Nothing to modify

        Player player = packetEvent.getPlayer();

        final Entity entity = getEntityById(player.getWorld(), entityId);
        if (entity == null) return;

        //Check if the entity is glowing
        if (!GlowAPI.isGlowing(entity, player)) return;

        //Update the DataWatcher Item
        final WrappedWatchableObject wrappedEntityObj = metaData.get(0);
        final Object entityObj = wrappedEntityObj.getValue();
        if (!(entityObj instanceof Byte)) return;
        byte entityByte = (byte) entityObj;
        /*Maybe use the isGlowing result*/
        entityByte = (byte) (entityByte | GlowAPI.ENTITY_GLOWING_EFFECT);
        wrappedEntityObj.setValue(entityByte);
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent packetEvent) { }

    @Override
    @NotNull
    public ListeningWhitelist getSendingWhitelist() {
        return ListeningWhitelist
            .newBuilder()
            .types(PacketType.Play.Server.ENTITY_METADATA)
            .priority(ListenerPriority.NORMAL)
            .gamePhase(GamePhase.PLAYING)
            .monitor()
            .build();
    }

    @Override
    @NotNull
    public ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist
            .newBuilder()
            .build();
    }

    @Override
    @NotNull
    public Plugin getPlugin() {
        return GlowAPI.getPlugin();
    }

    @Nullable
    private static Entity getEntityById(@NotNull World world,
                                        int entityId) {
        return world
            .getEntities()
            .parallelStream()
            .filter(entity -> entity.getEntityId() == entityId)
            .findAny()
            .orElse(null);
    }

}
