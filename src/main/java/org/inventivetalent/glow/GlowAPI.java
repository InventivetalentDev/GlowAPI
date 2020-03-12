package org.inventivetalent.glow;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.async.AsyncListenerHandler;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import lombok.Getter;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.glow.listeners.EntityMetadataListener;
import org.inventivetalent.glow.listeners.PlayerJoinListener;
import org.inventivetalent.glow.listeners.PlayerQuitListener;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerEntityMetadata;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.Modes;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class GlowAPI extends JavaPlugin {
	public static final byte ENTITY_GLOWING_EFFECT = (byte) 0x40;

	private static final WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);

	@Getter
	private static final Map<UUID, GlowData> dataMap = new ConcurrentHashMap<>();

	private Listener playerJoinListener;
	private Listener playerQuitListener;

	@Getter
	private ProtocolManager protocolManager;
	private AsynchronousManager asynchronousManager;
	private AsyncListenerHandler entityMetadataListenerHandler;

	/**
	 * Team Colors
	 */
	@SuppressWarnings("unused")
	public enum Color {
		BLACK(ChatColor.BLACK),
		DARK_BLUE(ChatColor.DARK_BLUE),
		DARK_GREEN(ChatColor.DARK_GREEN),
		DARK_AQUA(ChatColor.DARK_AQUA),
		DARK_RED(ChatColor.DARK_RED),
		DARK_PURPLE(ChatColor.DARK_PURPLE),
		GOLD(ChatColor.GOLD),
		GRAY(ChatColor.GRAY),
		DARK_GRAY(ChatColor.DARK_GRAY),
		BLUE(ChatColor.BLUE),
		GREEN(ChatColor.GREEN),
		AQUA(ChatColor.AQUA),
		RED(ChatColor.RED),
		PURPLE(ChatColor.LIGHT_PURPLE), // Kept for backwards compatibility
		LIGHT_PURPLE(ChatColor.LIGHT_PURPLE),
		YELLOW(ChatColor.YELLOW),
		WHITE(ChatColor.WHITE),
		NONE(ChatColor.RESET);

		@Getter
		ChatColor chatColor;

		Color(@NotNull ChatColor chatColor) {
			this.chatColor = chatColor;
		}

		@NotNull
		public String getTeamName() {
			String name = String.format("GAPI#%s", name());
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			return name;
		}
	}

	@NotNull
	public static GlowAPI getPlugin() { return getPlugin(GlowAPI.class); }

	@Override
	public void onEnable() {
		new MetricsLite(this, 2190);

		playerJoinListener = new PlayerJoinListener();
		playerQuitListener = new PlayerQuitListener();
		final PluginManager pluginManager = Bukkit.getPluginManager();
		pluginManager.registerEvents(playerJoinListener, this);
		pluginManager.registerEvents(playerQuitListener, this);

		protocolManager = ProtocolLibrary.getProtocolManager();
		asynchronousManager = protocolManager.getAsynchronousManager();

		final PacketListener entityMetadataListener = new EntityMetadataListener();
		entityMetadataListenerHandler = asynchronousManager.registerAsyncHandler(entityMetadataListener);
		entityMetadataListenerHandler.syncStart();
	}

	@Override
	public void onDisable() {
		PlayerJoinEvent.getHandlerList().unregister(playerJoinListener);
		PlayerQuitEvent.getHandlerList().unregister(playerQuitListener);

		asynchronousManager.unregisterAsyncHandler(entityMetadataListenerHandler);
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@Nullable Entity entity,
														  @Nullable GlowAPI.Color color,
											              @NotNull NameTagVisibility nameTagVisibility,
											              @NotNull TeamPush teamPush,
											              @NotNull Player player) {
		return CompletableFuture.supplyAsync(() -> {
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
			final Color oldColor = wasGlowing ? glowData.colorMap.get(playerUniqueId) : null;

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

			CompletableFuture<Void> future = GlowAPI.sendGlowPacketAsync(entity, glowing, player);

			final boolean createNewTeam = false;
			if (oldColor != null) {
				//We never add to NONE, so no need to remove
				if (oldColor != Color.NONE) {
					final boolean addEntity = false;
					//use the old color to remove the player from its team
					future.thenRun(() -> GlowAPI.sendTeamPacketAsync(entity, oldColor, createNewTeam, addEntity, nameTagVisibility, teamPush, player).join());
				}
			}
			if (glowing) {
				final boolean addEntity = color != Color.NONE;
				future.thenRun(() -> GlowAPI.sendTeamPacketAsync(entity, color, createNewTeam, addEntity, nameTagVisibility, teamPush, player).join());
			}

			future.join();

			return null;
		});
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@NotNull Collection<? extends Entity> entities,
														  @Nullable GlowAPI.Color color,
														  @NotNull NameTagVisibility nameTagVisibility,
														  @NotNull TeamPush teamPush,
														  @NotNull Player player) {
		Map<GlowAPI.Color, Collection<Entity>> removeFromTeam = new ConcurrentHashMap<>();
		Collection<Entity> addToTeam = ConcurrentHashMap.newKeySet();

		CompletableFuture<Void> future = CompletableFuture.allOf(entities
			.parallelStream()
			.map(entity -> {
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
				final Color oldColor = wasGlowing ? glowData.colorMap.get(playerUniqueId) : null;

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

				if (glowing) addToTeam.add(entity);

				if (oldColor != null) {
					//We never add to NONE, so no need to remove
					if (oldColor != Color.NONE) {
						if (!removeFromTeam.containsKey(oldColor)) {
							removeFromTeam.putIfAbsent(oldColor, ConcurrentHashMap.newKeySet());
						}
						Collection<Entity> teamEntities = removeFromTeam.get(oldColor);
						teamEntities.add(entity);
					}
				}

				return GlowAPI.sendGlowPacketAsync(entity, glowing, player);
			})
			.toArray(CompletableFuture[]::new));

		removeFromTeam
			.entrySet()
			.parallelStream()
			.forEach(entry -> {
				future.thenRun(() -> GlowAPI.sendTeamPacketAsync(entry.getValue(), entry.getKey(), false, nameTagVisibility, teamPush, player).join());
			});

		if (!addToTeam.isEmpty()) {
			final boolean addEntity = color != Color.NONE;
			future.thenRun(() -> GlowAPI.sendTeamPacketAsync(addToTeam, color, addEntity, nameTagVisibility, teamPush, player).join());
		}

		return future;
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull NameTagVisibility tagVisibility,
								  @NotNull TeamPush push,
								  @Nullable Player player) {
		setGlowingAsync(entity, color, tagVisibility, push, player).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@Nullable Entity entity,
							         	                  @Nullable GlowAPI.Color color,
								                          @NotNull Player player) {
		return setGlowingAsync(entity, color, NameTagVisibility.ALWAYS, TeamPush.ALWAYS, player);
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull Player player) {
		setGlowingAsync(entity, color, player).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@Nullable Entity entity,
								                          boolean glowing,
								                          @NotNull Player player) {
		return setGlowingAsync(entity, glowing ? GlowAPI.Color.NONE : null, player);
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  boolean glowing,
								  @NotNull Player player) {
		setGlowingAsync(entity, glowing, player).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@Nullable Entity entity,
							         		              boolean glowing,
														  @NotNull Collection<? extends Player> players) {
		return CompletableFuture.allOf(players
				.parallelStream()
				.map(player -> setGlowingAsync(entity, glowing, player))
				.toArray(CompletableFuture[]::new));
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  boolean glowing,
								  @NotNull Collection<? extends Player> players) {
		setGlowingAsync(entity, glowing, players).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@Nullable Entity entity,
											              @Nullable GlowAPI.Color color,
											              @NotNull Collection<? extends Player> players) {
		return CompletableFuture.allOf(players
			.parallelStream()
			.map(player -> setGlowingAsync(entity, color, player))
			.toArray(CompletableFuture[]::new));
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull Collection<? extends Player> players) {
		setGlowingAsync(entity, color, players).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@NotNull Collection<? extends Entity> entities,
														  @Nullable GlowAPI.Color color,
								                          @NotNull Player player) {
		return CompletableFuture.allOf(entities
			.parallelStream()
			.map(entity -> GlowAPI.setGlowingAsync(entity, color, player))
			.toArray(CompletableFuture[]::new));
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@NotNull Collection<? extends Entity> entities,
								  @Nullable GlowAPI.Color color,
								  @NotNull Player player) {
		setGlowingAsync(entities, color, player).join();
	}

	@NotNull
	public static CompletableFuture<Void> setGlowingAsync(@NotNull Collection<? extends Entity> entities,
							              	              @Nullable GlowAPI.Color color,
								                          @NotNull Collection<? extends Player> players) {
		return CompletableFuture.allOf(players
				.parallelStream()
				.map(player -> setGlowingAsync(entities, color, player))
				.toArray(CompletableFuture[]::new));
	}

	@SuppressWarnings("unused")
	public static void setGlowing(@NotNull Collection<? extends Entity> entities,
								  @Nullable GlowAPI.Color color,
								  @NotNull Collection<? extends Player> players) {
		setGlowingAsync(entities, color, players).join();
	}

	@SuppressWarnings("unused")
	public static boolean isGlowing(@NotNull Entity entity,
									@NotNull Player player) {
		return getGlowColor(entity, player) != null;
	}

	@SuppressWarnings("unused")
	public static boolean isGlowing(@NotNull Entity entity,
									@NotNull Collection<? extends Player> players,
									boolean checkAll) {
		Stream<? extends Player> playersStream =  players.parallelStream();
		if (checkAll) return playersStream.allMatch(player -> isGlowing(entity, player));
		else return playersStream.anyMatch(player -> isGlowing(entity, player));
	}

	@SuppressWarnings("unused")
	@Nullable public static GlowAPI.Color getGlowColor(@NotNull Entity entity,
													   @NotNull Player player) {
		final UUID entityUniqueId = entity.getUniqueId();
		if (!dataMap.containsKey(entityUniqueId)) return null;
		GlowData data = dataMap.get(entityUniqueId);
		return data.colorMap.get(player.getUniqueId());
	}

	@NotNull
	private static CompletableFuture<Void> sendGlowPacketAsync(@NotNull Entity entity,
												              boolean glowing,
												              @NotNull Player player) {
		return CompletableFuture.supplyAsync(() -> {
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
		});
	}

	@NotNull
	public static CompletableFuture<Void> initTeamsAsync(@NotNull Player player,
										                 @NotNull NameTagVisibility nameTagVisibility,
										                 @NotNull TeamPush teamPush) {
		return CompletableFuture.allOf(Arrays.stream(Color.values())
			.parallel()
			.map(color -> GlowAPI.sendTeamPacketAsync(null, color, true, false, nameTagVisibility, teamPush, player))
			.toArray(CompletableFuture[]::new));
	}

	@SuppressWarnings("unused")
	public static void initTeams(@NotNull Player player,
								 @NotNull NameTagVisibility nameTagVisibility,
								 @NotNull TeamPush teamPush) {
		initTeamsAsync(player, nameTagVisibility, teamPush).join();
	}

	@NotNull
	public static CompletableFuture<Void> initTeamsAsync(@NotNull Player player) {
		return initTeamsAsync(player, NameTagVisibility.ALWAYS, TeamPush.ALWAYS);
	}


	@SuppressWarnings("unused")
	public static void initTeams(@NotNull Player player) {
		initTeamsAsync(player).join();
	}

	@NotNull
	public static CompletableFuture<Void> sendTeamPacketAsync(@Nullable Entity entity,
												              @NotNull GlowAPI.Color color,
												              boolean createNewTeam,
												              boolean addEntity,
												              @NotNull NameTagVisibility nameTagVisibility,
												              @NotNull TeamPush teamPush,
												              @NotNull Player player) {
		return CompletableFuture.supplyAsync(() -> {
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
		});
	}

	@NotNull
	public static CompletableFuture<Void> sendTeamPacketAsync(@NotNull Collection<? extends Entity> entities,
															  @NotNull GlowAPI.Color color,
															  boolean addEntity,
															  @NotNull NameTagVisibility nameTagVisibility,
															  @NotNull TeamPush teamPush,
															  @NotNull Player player) {
		return CompletableFuture.supplyAsync(() -> {
			final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
			final WrapperPlayServerScoreboardTeam wrappedPacket = new WrapperPlayServerScoreboardTeam(packet);

			Modes packetMode;
			if (addEntity) packetMode = Modes.PLAYERS_ADDED;
			else packetMode = Modes.PLAYERS_REMOVED;

			final String teamName = color.getTeamName();

			wrappedPacket.setPacketMode(packetMode);
			wrappedPacket.setName(teamName);
			wrappedPacket.setNameTagVisibility(nameTagVisibility);
			wrappedPacket.setTeamPush(teamPush);

			Collection<String> entries = wrappedPacket.getEntries();
			entities
				.parallelStream()
				.map(entity -> {
					if (entity instanceof OfflinePlayer) {
						//Players still use the name...
						return entity.getName();
					} else {
						return entity.getUniqueId().toString();
					}
				})
				.forEach(entries::add);

			try {
				GlowAPI.getPlugin().getProtocolManager().sendServerPacket(player, packet);
			} catch (InvocationTargetException e) {
				throw new RuntimeException("Unable to send team packet to player " + player.toString(), e);
			}
			return null;
		});
	}

	@NotNull
	public static CompletableFuture<Entity> getEntityByIdAsync(@NotNull World world,
															   int entityId) {
		return CompletableFuture.supplyAsync(() -> world
			.getEntities()
			.parallelStream()
			.filter(entity -> entity.getEntityId() == entityId)
			.findAny()
			.orElse(null));
	}

	@Nullable
	@SuppressWarnings("unused")
	public static Entity getEntityById(@NotNull World world,
									   int entityId) {
		return getEntityByIdAsync(world, entityId).join();
	}

}
