package org.inventivetalent.glow;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.async.AsyncListenerHandler;
import com.comphenix.protocol.events.PacketListener;
import lombok.Getter;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.glow.callables.EntityById;
import org.inventivetalent.glow.callables.InitTeams;
import org.inventivetalent.glow.callables.SendGlowPacket;
import org.inventivetalent.glow.callables.SendTeamPacket;
import org.inventivetalent.glow.callables.SetGlowing;
import org.inventivetalent.glow.listeners.EntityMetadataListener;
import org.inventivetalent.glow.listeners.PlayerJoinListener;
import org.inventivetalent.glow.listeners.PlayerQuitListener;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.NameTagVisibility;
import org.inventivetalent.glow.packetwrappers.WrapperPlayServerScoreboardTeam.TeamPush;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class GlowAPI extends JavaPlugin {
	public static final byte ENTITY_GLOWING_EFFECT = (byte) 0x40;

	@Getter
	private static final Map<UUID, GlowData> dataMap = new ConcurrentHashMap<>();

	private Listener playerJoinListener;
	private Listener playerQuitListener;

	@Getter
	private ProtocolManager protocolManager;
	private AsynchronousManager asynchronousManager;
	private AsyncListenerHandler entityMetadataListenerHandler;

	@Getter
	private ExecutorService service;

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

		service = Executors.newSingleThreadExecutor();
	}

	@Override
	public void onDisable() {
		PlayerJoinEvent.getHandlerList().unregister(playerJoinListener);
		PlayerQuitEvent.getHandlerList().unregister(playerQuitListener);

		asynchronousManager.unregisterAsyncHandler(entityMetadataListenerHandler);

		service.shutdown();
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@Nullable Entity entity,
   										       @Nullable GlowAPI.Color color,
											   @NotNull NameTagVisibility nameTagVisibility,
											   @NotNull TeamPush teamPush,
											   @Nullable Player player) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new SetGlowing(entity, color, nameTagVisibility, teamPush, player);
		return service.submit(call);
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
		Future<Void> future = setGlowingAsync(entity, color, tagVisibility, push, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@Nullable Entity entity,
							         	       @Nullable GlowAPI.Color color,
								               @NotNull Player player) {
		return setGlowingAsync(entity, color, NameTagVisibility.ALWAYS, TeamPush.ALWAYS, player);
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
		Future<Void> future = setGlowingAsync(entity, color, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@Nullable Entity entity,
								               boolean glowing,
								               @NotNull Player player) {
		return setGlowingAsync(entity, glowing ? GlowAPI.Color.NONE : null, player);
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
		Future<Void> future = setGlowingAsync(entity, glowing, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@Nullable Entity entity,
							         		   boolean glowing,
								               @NotNull Collection<? extends Player> players) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				players
					.parallelStream()
					.map(player -> setGlowingAsync(entity, glowing, player))
					.forEach(future -> {
						try {
							future.get();
						} catch (InterruptedException | ExecutionException e) {
							throw new RuntimeException(e);
						}
					});
				return null;
			}

		};
		return service.submit(call);
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
		Future<Void> future = setGlowingAsync(entity, glowing, players);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@Nullable Entity entity,
											   @Nullable GlowAPI.Color color,
											   @NotNull Collection<? extends Player> players) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				players
						.parallelStream()
						.map(player -> setGlowingAsync(entity, color, player))
						.forEach(future -> {
							try {
								future.get();
							} catch (InterruptedException | ExecutionException e) {
								throw new RuntimeException(e);
							}
						});
				return null;
			}

		};
		return service.submit(call);
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
		Future<Void> future = setGlowingAsync(entity, color, players);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@NotNull Collection<? extends Entity> entities,
								               @Nullable GlowAPI.Color color,
								               @NotNull Player player) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				entities
					.parallelStream()
					.map(entity -> setGlowingAsync(entity, color, player))
					.forEach(future -> {
						try {
							future.get();
						} catch (InterruptedException | ExecutionException e) {
							throw new RuntimeException(e);
						}
					});
				return null;
			}

		};
		return service.submit(call);
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
		Future<Void> future = setGlowingAsync(entities, color, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> setGlowingAsync(@NotNull Collection<? extends Entity> entities,
							              	   @Nullable GlowAPI.Color color,
								               @NotNull Collection<? extends Player> players) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				entities
					.parallelStream()
					.map(entity -> setGlowingAsync(entity, color, players))
					.forEach(future -> {
						try {
							future.get();
						} catch (InterruptedException | ExecutionException e) {
							throw new RuntimeException(e);
						}
					});
				return null;
			}

		};
		return service.submit(call);
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
		Future<Void> future = setGlowingAsync(entities, color, players);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
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

	@NotNull
	public static Future<Void> sendGlowPacketAsync(@NotNull Entity entity,
												   boolean glowing,
												   @NotNull Player player) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new SendGlowPacket(entity, glowing, player);
		return service.submit(call);
	}

	public static void sendGlowPacket(@NotNull Entity entity,
									  boolean glowing,
									  @NotNull Player player) {
		Future<Void> future = sendGlowPacketAsync(entity, glowing, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> initTeamsAsync(@NotNull Player player,
										      @NotNull NameTagVisibility nameTagVisibility,
										      @NotNull TeamPush teamPush) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new InitTeams(player, nameTagVisibility, teamPush);
		return service.submit(call);
	}
	/**
	 * Initializes the teams for a player
	 *
	 * @param player            {@link Player} player
	 * @param nameTagVisibility {@link NameTagVisibility} visibility of the name-tag
	 * @param teamPush          {@link TeamPush} push behaviour
	 */
	@SuppressWarnings("unused")
	public static void initTeams(@NotNull Player player,
								 @NotNull NameTagVisibility nameTagVisibility,
								 @NotNull TeamPush teamPush) {
		Future<Void> future = initTeamsAsync(player, nameTagVisibility, teamPush);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> initTeamsAsync(@NotNull Player player) {
		return initTeamsAsync(player, NameTagVisibility.ALWAYS, TeamPush.ALWAYS);
	}
	/**
	 * Initializes the teams for a player
	 *
	 * @param player {@link Player} player
	 */
	@SuppressWarnings("unused")
	public static void initTeams(@NotNull Player player) {
		Future<Void> future = initTeamsAsync(player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Void> sendTeamPacketAsync(@Nullable Entity entity,
												   @NotNull GlowAPI.Color color,
												   boolean createNewTeam,
												   boolean addEntity,
												   @NotNull NameTagVisibility nameTagVisibility,
												   @NotNull TeamPush teamPush,
												   @NotNull Player player) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Void> call = new SendTeamPacket(entity, color, createNewTeam, addEntity, nameTagVisibility, teamPush, player);
		return service.submit(call);
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
	public static void sendTeamPacket(@Nullable Entity entity,
									  @NotNull GlowAPI.Color color,
									  boolean createNewTeam,
									  boolean addEntity,
									  @NotNull NameTagVisibility tagVisibility,
									  @NotNull TeamPush push,
									  @NotNull Player player) {
		Future<Void> future = sendTeamPacketAsync(entity, color, createNewTeam, addEntity, tagVisibility, push, player);
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	public static Future<Entity> getEntityByIdAsync(@NotNull World world,
													int entityId) {
		ExecutorService service = GlowAPI.getPlugin().getService();
		Callable<Entity> call = new EntityById(world, entityId);
		return service.submit(call);
	}

	@Nullable
	@SuppressWarnings("unused")
	public static Entity getEntityById(@NotNull World world,
									   int entityId) {
		Future<Entity> future = getEntityByIdAsync(world, entityId);
		try {
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

}
