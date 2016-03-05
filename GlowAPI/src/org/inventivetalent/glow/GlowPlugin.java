package org.inventivetalent.glow;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.handler.PacketHandler;
import org.inventivetalent.packetlistener.handler.PacketOptions;
import org.inventivetalent.packetlistener.handler.ReceivedPacket;
import org.inventivetalent.packetlistener.handler.SentPacket;
import org.inventivetalent.reflection.resolver.FieldResolver;
import org.inventivetalent.reflection.resolver.MethodResolver;
import org.inventivetalent.reflection.resolver.ResolverQuery;
import org.inventivetalent.reflection.resolver.minecraft.NMSClassResolver;
import org.inventivetalent.reflection.resolver.minecraft.OBCClassResolver;
import org.mcstats.MetricsLite;

import java.util.List;

public class GlowPlugin extends JavaPlugin implements Listener {

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);

		PacketHandler.addHandler(new PacketHandler(this) {
			@Override
			@PacketOptions(forcePlayer = true)
			public void onSend(SentPacket sentPacket) {
				if ("PacketPlayOutEntityMetadata".equals(sentPacket.getPacketName())) {
					int a = (int) sentPacket.getPacketValue("a");
					if (a < 0) {//Our packet
						//Reset the ID and let it through
						sentPacket.setPacketValue("a", -a);
						return;
					}

					List b = (List) sentPacket.getPacketValue("b");
					if (b == null || b.isEmpty()) {
						return;//Nothing to modify
					}

					Entity entity = getEntityById(sentPacket.getPlayer().getWorld(), a);
					if (entity != null) {
						//Check if the entity is glowing
						if (GlowAPI.isGlowing(entity, sentPacket.getPlayer())) {
							if (GlowAPI.DataWatcherItemMethodResolver == null) {
								GlowAPI.DataWatcherItemMethodResolver = new MethodResolver(GlowAPI.DataWatcherItem);
							}
							if (GlowAPI.DataWatcherItemFieldResolver == null) {
								GlowAPI.DataWatcherItemFieldResolver = new FieldResolver(GlowAPI.DataWatcherItem);
							}

							try {
								//Update the DataWatcher Item
								Object prevItem = b.get(0);
								byte prev = (byte) GlowAPI.DataWatcherItemMethodResolver.resolve("b").invoke(prevItem);
								byte bte = (byte) (true/*Maybe use the isGlowing result*/ ? (prev | 1 << 6) : (prev & ~(1 << 6)));//6 = glowing index
								GlowAPI.DataWatcherItemFieldResolver.resolve("b").set(prevItem, bte);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}

			@Override
			public void onReceive(ReceivedPacket receivedPacket) {
			}
		});

		try {
			MetricsLite metrics = new MetricsLite(this);
			if (metrics.start()) {
				getLogger().info("Metrics started");
			}
		} catch (Exception e) {
		}
	}

	@EventHandler
	public void onJoin(final PlayerJoinEvent event) {
		//Initialize the teams
		GlowAPI.initTeam(event.getPlayer());
	}

	@EventHandler
	public void onQuit(final PlayerQuitEvent event) {
		for (Player receiver : Bukkit.getOnlinePlayers()) {
			if (GlowAPI.isGlowing(event.getPlayer(), receiver)) {
				GlowAPI.setGlowing(event.getPlayer(), null, receiver);
			}
		}
	}

	protected static NMSClassResolver nmsClassResolver = new NMSClassResolver();
	protected static OBCClassResolver obcClassResolver = new OBCClassResolver();

	private static FieldResolver  CraftWorldFieldResolver;
	private static FieldResolver  WorldFieldResolver;
	private static MethodResolver IntHashMapMethodResolver;
	private static MethodResolver EntityMethodResolver;

	public static Entity getEntityById(World world, int entityId) {
		try {
			if (CraftWorldFieldResolver == null) {
				CraftWorldFieldResolver = new FieldResolver(obcClassResolver.resolve("CraftWorld"));
			}
			if (WorldFieldResolver == null) {
				WorldFieldResolver = new FieldResolver(nmsClassResolver.resolve("World"));
			}
			if (IntHashMapMethodResolver == null) {
				IntHashMapMethodResolver = new MethodResolver(nmsClassResolver.resolve("IntHashMap"));
			}
			if (EntityMethodResolver == null) {
				EntityMethodResolver = new MethodResolver(nmsClassResolver.resolve("Entity"));
			}

			Object entitiesById = WorldFieldResolver.resolve("entitiesById").get(CraftWorldFieldResolver.resolve("world").get(world));
			Object entity = IntHashMapMethodResolver.resolve(new ResolverQuery("get", int.class)).invoke(entitiesById, entityId);
			if (entity == null) { return null; }
			return (Entity) EntityMethodResolver.resolve("getBukkitEntity").invoke(entity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
