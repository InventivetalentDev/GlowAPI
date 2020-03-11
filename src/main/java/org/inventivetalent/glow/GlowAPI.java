package org.inventivetalent.glow;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.async.AsyncListenerHandler;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.glow.listeners.EntityMetadataListener;
import org.inventivetalent.glow.listeners.PlayerJoinListener;
import org.inventivetalent.glow.listeners.PlayerQuitListener;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerEntityMetadata;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.Modes;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class GlowAPI extends JavaPlugin {
	public static final byte ENTITY_GLOWING_EFFECT = (byte) 0x40;

	private static Map<UUID, GlowData> dataMap = new ConcurrentHashMap<>();

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

		ChatColor chatColor;

		Color(@NotNull ChatColor chatColor) {
			this.chatColor = chatColor;
		}

		@NotNull
		String getTeamName() {
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

		final PluginManager pluginManager = Bukkit.getPluginManager();
		pluginManager.registerEvents(new PlayerJoinListener(), this);
		pluginManager.registerEvents(new PlayerQuitListener(), this);

		protocolManager = ProtocolLibrary.getProtocolManager();
		asynchronousManager = protocolManager.getAsynchronousManager();

		final PacketListener entityMetadataListener = new EntityMetadataListener();
		entityMetadataListenerHandler = asynchronousManager.registerAsyncHandler(entityMetadataListener);
		entityMetadataListenerHandler.syncStart();
	}

	@Override
	public void onDisable() {
		asynchronousManager.unregisterAsyncHandler(entityMetadataListenerHandler);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity        {@link Entity} to update
	 * @param color         {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 * @param player        {@link Player} that will see the update
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull NameTagVisibility tagVisibility,
								  @NotNull TeamPush push,
								  @Nullable Player player) {
		if (player == null) return;

		boolean glowing = color != null;
		if (entity == null) glowing = false;
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) glowing = false;
		}

		final UUID entityUniqueId = (entity == null) ? null : entity.getUniqueId();
		final boolean wasGlowing = dataMap.containsKey(entityUniqueId);
		final GlowData glowData = (wasGlowing && entity != null) ? dataMap.get(entityUniqueId) : new GlowData();
		final UUID playerUniqueId = player.getUniqueId();
		final GlowAPI.Color oldColor = wasGlowing ? glowData.colorMap.get(playerUniqueId) : null;

		if (glowing) {
			glowData.colorMap.put(playerUniqueId, color);
		} else {
			glowData.colorMap.remove(playerUniqueId);
		}

		if (glowData.colorMap.isEmpty()) {
			dataMap.remove(entityUniqueId);
		} else if (entity != null) {
			dataMap.put(entityUniqueId, glowData);
		}

		if (color != null && oldColor == color) return;
		if (entity == null) return;
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) return;
		}
		if (!player.isOnline()) return;

		sendGlowPacket(entity, glowing, player);

		final boolean createNewTeam = false;
		if (oldColor != null) {
			//We never add to NONE, so no need to remove
			if (oldColor != GlowAPI.Color.NONE) {
				final boolean addEntity = false;
				//use the old color to remove the player from its team
				sendTeamPacket(entity, oldColor, createNewTeam, addEntity, tagVisibility, push, player);
			}
		}
		if (glowing) {
			final boolean addEntity = color != GlowAPI.Color.NONE;
			sendTeamPacket(entity, color, createNewTeam, addEntity, tagVisibility, push, player);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity {@link Entity} to update
	 * @param color  {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param player {@link Player} that will see the update
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull Player player) {
		setGlowing(entity, color, NameTagVisibility.ALWAYS, TeamPush.ALWAYS, player);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity  {@link Entity} to update
	 * @param glowing whether the entity is glowing or not
	 * @param player  {@link Player} that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  boolean glowing,
								  @NotNull Player player) {
		setGlowing(entity, glowing ? GlowAPI.Color.NONE : null, player);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity  {@link Entity} to update
	 * @param glowing whether the entity is glowing or not
	 * @param players Collection of {@link Player}s that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  boolean glowing,
								  @NotNull Collection<? extends Player> players) {
		players
			.parallelStream()
			.forEach(player -> setGlowing(entity, glowing, player));
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity  {@link Entity} to update
	 * @param color   {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param players Collection of {@link Player}s that will see the update
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@Nullable Entity entity,
								  @Nullable GlowAPI.Color color,
								  @NotNull Collection<? extends Player> players) {
		players
			.parallelStream()
			.forEach(player -> setGlowing(entity, color, player));
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities Collection of {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param player   {@link Player} that will see the update
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@NotNull Collection<? extends Entity> entities,
								  @Nullable GlowAPI.Color color,
								  @NotNull Player player) {
		entities
			.parallelStream()
			.forEach(entity -> setGlowing(entity, color, player));
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities Collection of {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param players  Collection of {@link Player}s that will see the update
	 */
	@SuppressWarnings("unused")
	public static void setGlowing(@NotNull Collection<? extends Entity> entities,
								  @Nullable GlowAPI.Color color,
								  @NotNull Collection<? extends Player> players) {
		entities
			.parallelStream()
			.forEach(entity -> setGlowing(entity, color, players));
	}

	/**
	 * Check if an entity is glowing
	 *
	 * @param entity {@link Entity} to check
	 * @param player {@link Player} player to check (as used in the setGlowing methods)
	 * @return <code>true</code> if the entity appears glowing to the player
	 */
	@SuppressWarnings("unused")
	public static boolean isGlowing(@NotNull Entity entity,
									@NotNull Player player) {
		return getGlowColor(entity, player) != null;
	}

	/**
	 * Checks if an entity is glowing
	 *
	 * @param entity   {@link Entity} to check
	 * @param players  Collection of {@link Player} players to check
	 * @param checkAll if <code>true</code>, this only returns <code>true</code> if the entity is glowing for all players; if <code>false</code> this returns <code>true</code> if the entity is glowing for any of the players
	 * @return <code>true</code> if the entity appears glowing to the players
	 */
	@SuppressWarnings("unused")
	public static boolean isGlowing(@NotNull Entity entity,
									@NotNull Collection<? extends Player> players,
									boolean checkAll) {
		Stream<? extends Player> playersStream =  players.parallelStream();
		if (checkAll) return playersStream.allMatch(player -> isGlowing(entity, player));
		else return playersStream.anyMatch(player -> isGlowing(entity, player));
	}

	/**
	 * Get the glow-color of an entity
	 *
	 * @param entity {@link Entity} to get the color for
	 * @param player {@link Player} player of the color (as used in the setGlowing methods)
	 * @return the {@link GlowAPI.Color}, or <code>null</code> if the entity doesn't appear glowing to the player
	 */
	@SuppressWarnings("unused")
	@Nullable public static GlowAPI.Color getGlowColor(@NotNull Entity entity,
													   @NotNull Player player) {
		final UUID entityUniqueId = entity.getUniqueId();
		if (!dataMap.containsKey(entityUniqueId)) return null;
		GlowData data = dataMap.get(entityUniqueId);
		return data.colorMap.get(player.getUniqueId());
	}

	protected static void sendGlowPacket(@NotNull Entity entity,
										 boolean glowing,
										 @NotNull Player player) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
		final WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);
		final WrappedDataWatcher.WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcher.WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class));

		final int invertedEntityId = -entity.getEntityId();

		final WrappedDataWatcher dataWatcher = WrappedDataWatcher.getEntityWatcher(entity);
		final List<WrappedWatchableObject> dataWatcherObjects = dataWatcher.getWatchableObjects();
		byte entityByte = (dataWatcherObjects.isEmpty()) ? 0x00 : (byte) dataWatcherObjects.get(0).getValue();
		entityByte = (byte) (glowing ? (entityByte | ENTITY_GLOWING_EFFECT) : (entityByte & ~ENTITY_GLOWING_EFFECT));

		final WrappedWatchableObject wrappedMetadata = new WrappedWatchableObject(dataWatcherObject, entityByte);
		final List<WrappedWatchableObject> metadata = Collections.singletonList(wrappedMetadata);

		wrappedPacket.setEntityID(invertedEntityId);
		wrappedPacket.setMetadata(metadata);

		try {
			GlowAPI.getPlugin().protocolManager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + player.toString(), e);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param player        {@link Player} player
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	@SuppressWarnings("unused")
	public static void initTeam(@NotNull Player player,
								@NotNull NameTagVisibility tagVisibility,
								@NotNull TeamPush push) {
		Arrays.stream(Color.values())
			.parallel()
			.forEach(color -> GlowAPI.sendTeamPacket(null, color, true, false, tagVisibility, push, player));
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param player {@link Player} player
	 */
	@SuppressWarnings("unused")
	public static void initTeam(@NotNull Player player) {
		initTeam(player, NameTagVisibility.ALWAYS, TeamPush.ALWAYS);
	}

	/**
	 *
	 * @param entity 		{@link Entity} Entity to set glowing status of
	 * @param color 		{@link GlowAPI.Color} Color of glow
	 * @param createNewTeam If true, we don't add any entities
	 * @param addEntity 	true->add the entity, false->remove the entity
	 * @param tagVisibility {@link NameTagVisibility} Name tag visiblity for team
	 * @param push 			{@link TeamPush} Collision options for team
	 * @param player 		{@link Player} Player packet is targeted at
	 */
	protected static void sendTeamPacket(@Nullable Entity entity,
										 @NotNull GlowAPI.Color color,
										 boolean createNewTeam,
										 boolean addEntity,
										 @NotNull NameTagVisibility tagVisibility,
										 @NotNull TeamPush push,
										 @NotNull Player player) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
		final WrapperPlayServerScoreboardTeam wrappedPacket = new WrapperPlayServerScoreboardTeam(packet);

		final Modes packetMode = (createNewTeam ? Modes.TEAM_CREATED : (addEntity ? Modes.PLAYERS_ADDED : Modes.PLAYERS_REMOVED));
		final String teamName = color.getTeamName();

		wrappedPacket.setPacketMode(packetMode);
		wrappedPacket.setName(teamName);
		wrappedPacket.setNameTagVisibility(tagVisibility);
		wrappedPacket.setTeamPush(push);

		if (createNewTeam) {
			wrappedPacket.setTeamColor(color.chatColor);
			wrappedPacket.setTeamPrefix(color.chatColor.toString());
			wrappedPacket.setTeamDisplayName(teamName);
			wrappedPacket.setTeamSuffix("");
			wrappedPacket.setAllowFriendlyFire(true);
			wrappedPacket.setCanSeeFriendlyInvisibles(false);
		} else {
			if (entity == null) return;
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
			GlowAPI.getPlugin().protocolManager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + player.toString(), e);
		}
	}

	@SuppressWarnings("unused")
	@Nullable
	public static Entity getEntityById(@NotNull World world,
									   int entityId) {
		return world
			.getEntities()
			.parallelStream()
			.filter(entity -> entity.getEntityId() == entityId)
			.findAny()
			.orElse(null);
	}

}
