package org.inventivetalent.glow;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.glow.listeners.PlayerJoinListener;
import org.inventivetalent.glow.listeners.PlayerQuitListener;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerEntityMetadata;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.Modes;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrapper.WrapperPlayServerScoreboardTeam.TeamPush;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlowAPI extends JavaPlugin {

	// From https://wiki.vg/Entity_metadata#Entity
	public static final byte ENTITY_ON_FIRE            = (byte) 0x01;
	public static final byte ENTITY_CROUCHED           = (byte) 0x02;
	public static final byte ENTITY_RIDING             = (byte) 0x04; // UNUSED
	public static final byte ENTITY_SPRINTING          = (byte) 0x08;
	public static final byte ENTITY_SWIMMING           = (byte) 0x10;
	public static final byte ENTITY_INVISIBLE          = (byte) 0x20;
	public static final byte ENTITY_GLOWING_EFFECT     = (byte) 0x40;
	public static final byte ENTITY_FLYING_WITH_ELYTRA = (byte) 0x80;

	private static Map<UUID, GlowData> dataMap = new ConcurrentHashMap<>();

	/**
	 * Team Colors
	 */
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
		PURPLE(ChatColor.LIGHT_PURPLE),
		YELLOW(ChatColor.YELLOW),
		WHITE(ChatColor.WHITE),
		NONE(ChatColor.RESET);

		ChatColor chatColor;

		Color(ChatColor chatColor) {
			this.chatColor = chatColor;
		}

		String getTeamName() {
			String name = String.format("GAPI#%s", name());
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			return name;
		}
	}

	public static GlowAPI getPlugin() {
		return getPlugin(GlowAPI.class);
	}

	@Override
	public void onEnable() {
		new MetricsLite(this, 2190);

		Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
		Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);

		final AsynchronousManager protocolManager = ProtocolLibrary.getProtocolManager().getAsynchronousManager();
		protocolManager.registerAsyncHandler(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {

			@Override
			public void onPacketSending(PacketEvent event) {
				PacketContainer packet = event.getPacket();
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

				Player player = event.getPlayer();

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
				entityByte = (byte) (entityByte | ENTITY_GLOWING_EFFECT);
				wrappedEntityObj.setValue(entityByte);
			}

		}).syncStart();
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity        {@link Entity} to update
	 * @param color         {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 * @param player      {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, NameTagVisibility tagVisibility, TeamPush push, Player player) {
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
		} else {
			if (entity != null) dataMap.put(entityUniqueId, glowData);
		}

		if (color != null && oldColor == color) return;
		if (entity == null) return;
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) return;
		}
		if (!player.isOnline()) return;

		sendGlowPacket(entity, wasGlowing, glowing, player);
		if (oldColor != null && oldColor != GlowAPI.Color.NONE/*We never add to NONE, so no need to remove*/) {
			sendTeamPacket(entity, oldColor/*use the old color to remove the player from its team*/, false, false, tagVisibility, push, player);
		}
		if (glowing) {
			sendTeamPacket(entity, color, false, color != GlowAPI.Color.NONE, tagVisibility, push, player);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity   {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param player {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, Player player) {
		setGlowing(entity, color, NameTagVisibility.ALWAYS, TeamPush.ALWAYS, player);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity   {@link Entity} to update
	 * @param glowing  whether the entity is glowing or not
	 * @param player {@link Player} that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Player player) {
		setGlowing(entity, glowing ? GlowAPI.Color.NONE : null, player);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity    {@link Entity} to update
	 * @param glowing   whether the entity is glowing or not
	 * @param players Collection of {@link Player}s that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Collection<? extends Player> players) {
		for (Player player : players) {
			setGlowing(entity, glowing, player);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity    {@link Entity} to update
	 * @param color     {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param players Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, Collection<? extends Player> players) {
		for (Player player : players) {
			setGlowing(entity, color, player);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities Collection of {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param player {@link Player} that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, GlowAPI.Color color, Player player) {
		for (Entity entity : entities) {
			setGlowing(entity, color, player);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities  Collection of {@link Entity} to update
	 * @param color     {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param players Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, GlowAPI.Color color, Collection<? extends Player> players) {
		for (Entity entity : entities) {
			setGlowing(entity, color, players);
		}
	}

	/**
	 * Check if an entity is glowing
	 *
	 * @param entity   {@link Entity} to check
	 * @param player {@link Player} player to check (as used in the setGlowing methods)
	 * @return <code>true</code> if the entity appears glowing to the player
	 */
	public static boolean isGlowing(Entity entity, Player player) {
		return getGlowColor(entity, player) != null;
	}

	/**
	 * Checks if an entity is glowing
	 *
	 * @param entity    {@link Entity} to check
	 * @param players Collection of {@link Player} players to check
	 * @param checkAll  if <code>true</code>, this only returns <code>true</code> if the entity is glowing for all players; if <code>false</code> this returns <code>true</code> if the entity is glowing for any of the players
	 * @return <code>true</code> if the entity appears glowing to the players
	 */
	public static boolean isGlowing(Entity entity, Collection<? extends Player> players, boolean checkAll) {
		if (checkAll) {
			boolean glowing = true;
			for (Player player : players) {
				if (!isGlowing(entity, player)) {
					glowing = false;
				}
			}
			return glowing;
		} else {
			for (Player player : players) {
				if (isGlowing(entity, player)) { return true; }
			}
		}
		return false;
	}

	/**
	 * Get the glow-color of an entity
	 *
	 * @param entity   {@link Entity} to get the color for
	 * @param player {@link Player} player of the color (as used in the setGlowing methods)
	 * @return the {@link GlowAPI.Color}, or <code>null</code> if the entity doesn't appear glowing to the player
	 */
	public static GlowAPI.Color getGlowColor(Entity entity, Player player) {
		final UUID entityUniqueId = entity.getUniqueId();
		if (!dataMap.containsKey(entityUniqueId)) return null;
		GlowData data = dataMap.get(entityUniqueId);
		return data.colorMap.get(player.getUniqueId());
	}

	protected static void sendGlowPacket(Entity entity, boolean wasGlowing, boolean glowing, Player player) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
		final WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);
		final WrappedDataWatcher.WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcher.WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class));

		final int invertedEntityId = -entity.getEntityId();

		byte entityByte = 0x00;
		entityByte = (byte) (glowing ? (entityByte | ENTITY_GLOWING_EFFECT) : (entityByte & ~ENTITY_GLOWING_EFFECT));

		final WrappedWatchableObject wrappedMetadata = new WrappedWatchableObject(dataWatcherObject, entityByte);
		final List<WrappedWatchableObject> metadata = Collections.singletonList(wrappedMetadata);

		wrappedPacket.setEntityID(invertedEntityId);
		wrappedPacket.setMetadata(metadata);

		final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		try {
			protocolManager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + player.toString(), e);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param player      {@link Player} player
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	public static void initTeam(Player player, NameTagVisibility tagVisibility, TeamPush push) {
		for (GlowAPI.Color color : GlowAPI.Color.values()) {
			GlowAPI.sendTeamPacket(null, color, true, false, tagVisibility, push, player);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param player {@link Player} player
	 */
	public static void initTeam(Player player) {
		initTeam(player, NameTagVisibility.ALWAYS, TeamPush.ALWAYS);
	}

	/**
	 *
	 * @param entity
	 * @param color
	 * @param createNewTeam - If true, we don't add any entities
	 * @param addEntity - true->add the entity, false->remove the entity
	 * @param tagVisibility
	 * @param push
	 * @param player
	 */
	protected static void sendTeamPacket(Entity entity, GlowAPI.Color color, boolean createNewTeam, boolean addEntity, NameTagVisibility tagVisibility, TeamPush push, Player player) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
		final WrapperPlayServerScoreboardTeam wrappedPacket = new WrapperPlayServerScoreboardTeam(packet);

		final Modes packetMode = (createNewTeam ? Modes.TEAM_CREATED : (addEntity ? Modes.PLAYERS_ADDED : Modes.PLAYERS_REMOVED));
		final String teamName = color.getTeamName();

		wrappedPacket.setPacketMode(packetMode);
		wrappedPacket.setTeamName(teamName);
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
			//Add/remove players
			String entry;
			if (entity instanceof OfflinePlayer) {//Players still use the name...
				entry = entity.getName();
			} else {
				entry = entity.getUniqueId().toString();
			}
			Collection<String> players = wrappedPacket.getPlayers();
			players.add(entry);
		}

		final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		try {
			protocolManager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + player.toString(), e);
		}
	}

	public static Entity getEntityById(World world, int entityId) {
		return world
			.getEntities()
			.parallelStream()
			.filter(entity -> entity.getEntityId() == entityId)
			.findAny()
			.orElse(null);
	}

}
