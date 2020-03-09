package org.inventivetalent.glowapi;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftReflection;
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
import org.inventivetalent.glowapi.listeners.PlayerJoinListener;
import org.inventivetalent.glowapi.listeners.PlayerQuitListener;
import org.inventivetalent.glowapi.packetwrapper.WrapperPlayServerEntityMetadata;
import org.inventivetalent.glowapi.packetwrapper.WrapperPlayServerScoreboardTeam;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

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

	private static Map<UUID, GlowData> dataMap = new HashMap<>();
	static boolean isPaper = false;

	static {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			isPaper = true;
		} catch (Exception ignored) {
			isPaper = false;
		}
	}

	//Options
	/**
	 * Default name-tag visibility (always, hideForOtherTeams, hideForOwnTeam, never)
	 */
	public static String TEAM_TAG_VISIBILITY = "always";
	/**
	 * Default push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	public static String TEAM_PUSH           = "always";

	/**
	 * Team Colors
	 */
	public enum Color {

		BLACK(ChatColor.BLACK, "0"),
		DARK_BLUE(ChatColor.DARK_BLUE, "1"),
		DARK_GREEN(ChatColor.DARK_GREEN, "2"),
		DARK_AQUA(ChatColor.DARK_AQUA, "3"),
		DARK_RED(ChatColor.RED, "4"),
		DARK_PURPLE(ChatColor.DARK_PURPLE, "5"),
		GOLD(ChatColor.GOLD, "6"),
		GRAY(ChatColor.GRAY, "7"),
		DARK_GRAY(ChatColor.DARK_GRAY, "8"),
		BLUE(ChatColor.BLUE, "9"),
		GREEN(ChatColor.GREEN, "a"),
		AQUA(ChatColor.AQUA, "b"),
		RED(ChatColor.RED, "c"),
		PURPLE(ChatColor.LIGHT_PURPLE, "d"),
		YELLOW(ChatColor.YELLOW, "e"),
		WHITE(ChatColor.WHITE, "f"),
		NONE(ChatColor.RESET, "");

		ChatColor packetValue;
		String colorCode;

		Color(ChatColor packetValue, String colorCode) {
			this.packetValue = packetValue;
			this.colorCode = colorCode;
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

		ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {

			@Override
			public void onPacketSending(PacketEvent event) {
				PacketContainer packet = event.getPacket();
				WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);

				final int entityId = wrappedPacket.getEntityID();
				if (entityId < 0) {//Our packet
					//Reset the ID and let it through
					wrappedPacket.setEntityID(-entityId);
					return;
				}

				final List<WrappedWatchableObject> metaData = wrappedPacket.getMetadata();
				if (metaData == null || metaData.isEmpty()) return;//Nothing to modify

				Player player = event.getPlayer();

				Entity entity = getEntityById(player.getWorld(), entityId);
				if (entity == null) return;

				//Check if the entity is glowing
				if (!GlowAPI.isGlowing(entity, player)) return;

				//Update the DataWatcher Item
				//Object prevItem = b.get(0);
				for (WrappedWatchableObject prevItem : metaData) {
					Object prevObj = prevItem.getValue();
					if (prevObj instanceof Byte) {
						byte prev = (byte) prevObj;
						/*Maybe use the isGlowing result*/
						byte bte = (byte) ((prev | ENTITY_GLOWING_EFFECT));
						prevItem.setValue(bte);
					}
				}
			}

		});
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity        {@link Entity} to update
	 * @param color         {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 * @param receiver      {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, String tagVisibility, String push, Player receiver) {
		if (receiver == null) { return; }

		boolean glowing = color != null;
		if (entity == null) { glowing = false; }
		if (entity instanceof OfflinePlayer) {
			if (!((OfflinePlayer) entity).isOnline()) { glowing = false; }
		}

		boolean wasGlowing = dataMap.containsKey(entity != null ? entity.getUniqueId() : null);
		GlowData glowData;
		if (wasGlowing && entity != null) { glowData = dataMap.get(entity.getUniqueId()); } else { glowData = new GlowData(); }

		GlowAPI.Color oldColor = wasGlowing ? glowData.colorMap.get(receiver.getUniqueId()) : null;

		if (glowing) {
			glowData.colorMap.put(receiver.getUniqueId(), color);
		} else {
			glowData.colorMap.remove(receiver.getUniqueId());
		}
		if (glowData.colorMap.isEmpty()) {
			dataMap.remove(entity != null ? entity.getUniqueId() : null);
		} else {
			if (entity != null) {
				dataMap.put(entity.getUniqueId(), glowData);
			}
		}

		if (color != null && oldColor == color) { return; }
		if (entity == null) { return; }
		if (entity instanceof OfflinePlayer) { if (!((OfflinePlayer) entity).isOnline()) { return; } }
		if (!receiver.isOnline()) { return; }

		sendGlowPacket(entity, wasGlowing, glowing, receiver);
		if (oldColor != null && oldColor != GlowAPI.Color.NONE/*We never add to NONE, so no need to remove*/) {
			sendTeamPacket(entity, oldColor/*use the old color to remove the player from its team*/, false, false, tagVisibility, push, receiver);
		}
		if (glowing) {
			sendTeamPacket(entity, color, false, color != GlowAPI.Color.NONE, tagVisibility, push, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity   {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param receiver {@link Player} that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, Player receiver) {
		setGlowing(entity, color, "always", "always", receiver);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity   {@link Entity} to update
	 * @param glowing  whether the entity is glowing or not
	 * @param receiver {@link Player} that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Player receiver) {
		setGlowing(entity, glowing ? GlowAPI.Color.NONE : null, receiver);
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity    {@link Entity} to update
	 * @param glowing   whether the entity is glowing or not
	 * @param receivers Collection of {@link Player}s that will see the update
	 * @see #setGlowing(Entity, GlowAPI.Color, Player)
	 */
	public static void setGlowing(Entity entity, boolean glowing, Collection<? extends Player> receivers) {
		for (Player receiver : receivers) {
			setGlowing(entity, glowing, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entity    {@link Entity} to update
	 * @param color     {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param receivers Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Entity entity, GlowAPI.Color color, Collection<? extends Player> receivers) {
		for (Player receiver : receivers) {
			setGlowing(entity, color, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities Collection of {@link Entity} to update
	 * @param color    {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param receiver {@link Player} that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, GlowAPI.Color color, Player receiver) {
		for (Entity entity : entities) {
			setGlowing(entity, color, receiver);
		}
	}

	/**
	 * Set the glowing-color of an entity
	 *
	 * @param entities  Collection of {@link Entity} to update
	 * @param color     {@link GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
	 * @param receivers Collection of {@link Player}s that will see the update
	 */
	public static void setGlowing(Collection<? extends Entity> entities, GlowAPI.Color color, Collection<? extends Player> receivers) {
		for (Entity entity : entities) {
			setGlowing(entity, color, receivers);
		}
	}

	/**
	 * Check if an entity is glowing
	 *
	 * @param entity   {@link Entity} to check
	 * @param receiver {@link Player} receiver to check (as used in the setGlowing methods)
	 * @return <code>true</code> if the entity appears glowing to the player
	 */
	public static boolean isGlowing(Entity entity, Player receiver) {
		return getGlowColor(entity, receiver) != null;
	}

	/**
	 * Checks if an entity is glowing
	 *
	 * @param entity    {@link Entity} to check
	 * @param receivers Collection of {@link Player} receivers to check
	 * @param checkAll  if <code>true</code>, this only returns <code>true</code> if the entity is glowing for all receivers; if <code>false</code> this returns <code>true</code> if the entity is glowing for any of the receivers
	 * @return <code>true</code> if the entity appears glowing to the players
	 */
	public static boolean isGlowing(Entity entity, Collection<? extends Player> receivers, boolean checkAll) {
		if (checkAll) {
			boolean glowing = true;
			for (Player receiver : receivers) {
				if (!isGlowing(entity, receiver)) {
					glowing = false;
				}
			}
			return glowing;
		} else {
			for (Player receiver : receivers) {
				if (isGlowing(entity, receiver)) { return true; }
			}
		}
		return false;
	}

	/**
	 * Get the glow-color of an entity
	 *
	 * @param entity   {@link Entity} to get the color for
	 * @param receiver {@link Player} receiver of the color (as used in the setGlowing methods)
	 * @return the {@link GlowAPI.Color}, or <code>null</code> if the entity doesn't appear glowing to the player
	 */
	public static GlowAPI.Color getGlowColor(Entity entity, Player receiver) {
		if (!dataMap.containsKey(entity.getUniqueId())) { return null; }
		GlowData data = dataMap.get(entity.getUniqueId());
		return data.colorMap.get(receiver.getUniqueId());
	}

	protected static void sendGlowPacket(Entity entity, boolean wasGlowing, boolean glowing, Player receiver) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
		final WrapperPlayServerEntityMetadata wrappedPacket = new WrapperPlayServerEntityMetadata(packet);
		final WrappedDataWatcher.WrappedDataWatcherObject dataWatcherObject = new WrappedDataWatcher.WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class));

		final int invertedEntityId = -entity.getEntityId();

		byte entityByte = 0x00;
		if (glowing) entityByte = (byte) (entityByte | ENTITY_GLOWING_EFFECT);

		final WrappedWatchableObject wrappedMetadata = new WrappedWatchableObject(dataWatcherObject, entityByte);
		final List<WrappedWatchableObject> metadata = Collections.singletonList(wrappedMetadata);

		wrappedPacket.setEntityID(invertedEntityId);
		wrappedPacket.setMetadata(metadata);

		final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		try {
			protocolManager.sendServerPacket(receiver, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + receiver.toString(), e);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param receiver      {@link Player} receiver
	 * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
	 * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
	 */
	public static void initTeam(Player receiver, String tagVisibility, String push) {
		for (GlowAPI.Color color : GlowAPI.Color.values()) {
			GlowAPI.sendTeamPacket(null, color, true, false, tagVisibility, push, receiver);
		}
	}

	/**
	 * Initializes the teams for a player
	 *
	 * @param receiver {@link Player} receiver
	 */
	public static void initTeam(Player receiver) {
		initTeam(receiver, TEAM_TAG_VISIBILITY, TEAM_PUSH);
	}

	/**
	 *
	 * @param entity
	 * @param color
	 * @param createNewTeam - If true, we don't add any entities
	 * @param addEntity - true->add the entity, false->remove the entity
	 * @param tagVisibility
	 * @param push
	 * @param receiver
	 */
	protected static void sendTeamPacket(Entity entity, GlowAPI.Color color, boolean createNewTeam, boolean addEntity, String tagVisibility, String push, Player receiver) {
		final PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
		final WrapperPlayServerScoreboardTeam wrappedPacket = new WrapperPlayServerScoreboardTeam(packet);

		//Mode (0 = create, 3 = add entity, 4 = remove entity)
		final byte packetMode = (byte) (createNewTeam ? 0 : (addEntity ? 3 : 4));
		final String teamName = color.getTeamName();

		wrappedPacket.setPacketMode(packetMode);
		wrappedPacket.setTeamName(teamName);

		if (createNewTeam) {
			wrappedPacket.setTeamColor((ChatColor) color.packetValue);
			wrappedPacket.setTeamPrefix("§" + color.colorCode);
			wrappedPacket.setTeamDisplayName(teamName);
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
			protocolManager.sendServerPacket(receiver, packet);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Unable to send packet " + packet.toString() + " to player " + receiver.toString(), e);
		}
	}

	public static Entity getEntityById(World world, int entityId) {
		for (Entity entity : world.getEntities()) {
			if (entity.getEntityId() != entityId) continue;
			return entity;
		}
		return null;
	}

}
