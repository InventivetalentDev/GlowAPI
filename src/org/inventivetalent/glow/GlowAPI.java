package org.inventivetalent.glow;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.inventivetalent.packetlistener.handler.PacketHandler;
import org.inventivetalent.packetlistener.handler.PacketOptions;
import org.inventivetalent.packetlistener.handler.ReceivedPacket;
import org.inventivetalent.packetlistener.handler.SentPacket;
import org.inventivetalent.reflection.minecraft.Minecraft;
import org.inventivetalent.reflection.minecraft.MinecraftVersion;
import org.inventivetalent.reflection.resolver.ConstructorResolver;
import org.inventivetalent.reflection.resolver.FieldResolver;
import org.inventivetalent.reflection.resolver.MethodResolver;
import org.inventivetalent.reflection.resolver.ResolverQuery;
import org.inventivetalent.reflection.resolver.minecraft.NMSClassResolver;
import org.inventivetalent.reflection.resolver.minecraft.OBCClassResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlowAPI extends PacketHandler implements Listener {

    private static Map<UUID, GlowData> dataMap = new HashMap<>();
    private final static Map<Integer, UUID> entityById = new ConcurrentHashMap<>();

    private static final NMSClassResolver NMS_CLASS_RESOLVER = new NMSClassResolver();

    //Metadata
    private static Class<?> PacketPlayOutEntityMetadata;
    static Class<?> DataWatcher;
    static Class<?> DataWatcherItem;
    private static Class<?> Entity;

    private static FieldResolver PacketPlayOutMetadataFieldResolver;
    private static FieldResolver EntityFieldResolver;
    private static FieldResolver DataWatcherFieldResolver;
    static FieldResolver DataWatcherItemFieldResolver;
    private static FieldResolver DataWatcherItemAccessorFieldResolver; // >= 1.19

    private static ConstructorResolver PacketPlayOutMetadataResolver;
    private static ConstructorResolver DataWatcherItemConstructorResolver;

    private static MethodResolver DataWatcherMethodResolver;
    static MethodResolver DataWatcherItemMethodResolver;
    private static MethodResolver EntityMethodResolver;

    //Scoreboard
    private static Object nms$Scoreboard;
    private static ArrayList<String> scoreboardTeamEntityList;

    private static Class<?> Scoreboard; // >= 1.17
    private static Class<?> ScoreboardTeam; // >= 1.17
    private static Class<?> PacketPlayOutScoreboardTeam;
    private static Class<?> PacketPlayOutScoreboardTeam$info; // >= 1.17

    private static FieldResolver PacketScoreboardTeamFieldResolver;

    private static MethodResolver ScoreboardTeamMethodResolver; // >= 1.17

    private static ConstructorResolver ScoreboardResolver; // >= 1.17
    private static ConstructorResolver ScoreboardTeamResolver; // >= 1.17

    private static ConstructorResolver PacketScoreboardTeamResolver; // >= 1.17
    private static ConstructorResolver PacketScoreboardTeamInfoResolver; // >= 1.17

    private static ConstructorResolver ChatComponentTextResolver;
    private static MethodResolver EnumChatFormatResolver;
    private static MethodResolver IChatBaseComponentMethodResolver;

    //Packets
    private static FieldResolver EntityPlayerFieldResolver;
    private static MethodResolver PlayerConnectionMethodResolver;

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
    public static String TEAM_PUSH = "always";

    /**
     * Set the glowing-color of an entity
     *
     * @param entity        {@link Entity} to update
     * @param color         {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
     * @param tagVisibility visibility of the name-tag (always, hideForOtherTeams, hideForOwnTeam, never)
     * @param push          push behaviour (always, pushOtherTeams, pushOwnTeam, never)
     * @param receiver      {@link Player} that will see the update
     */
    public static void setGlowing(Entity entity, Color color, String tagVisibility, String push, Player receiver) {
        if (receiver == null) {return;}

        boolean glowing = color != null;
        if (entity == null) {glowing = false;}
        if (entity instanceof OfflinePlayer) {if (!((OfflinePlayer) entity).isOnline()) {glowing = false;}}

        boolean wasGlowing = dataMap.containsKey(entity != null ? entity.getUniqueId() : null);
        GlowData glowData;
        if (wasGlowing && entity != null) {glowData = dataMap.get(entity.getUniqueId());} else {glowData = new GlowData();}

        Color oldColor = wasGlowing ? glowData.colorMap.get(receiver.getUniqueId()) : null;

        if (glowing) {
            glowData.colorMap.put(receiver.getUniqueId(), color);
            entityById.put(entity.getEntityId(), entity.getUniqueId());
        } else {
            glowData.colorMap.remove(receiver.getUniqueId());
        }
        if (glowData.colorMap.isEmpty()) {
            dataMap.remove(entity != null ? entity.getUniqueId() : null);
            if (entity != null) entityById.remove(entity.getEntityId());
        } else {
            if (entity != null) {
                dataMap.put(entity.getUniqueId(), glowData);
            }
        }

        if (color != null && oldColor == color) {return;}
        if (entity == null) {return;}
        if (entity instanceof OfflinePlayer) {if (!((OfflinePlayer) entity).isOnline()) {return;}}
        if (!receiver.isOnline()) {return;}

        sendGlowPacket(entity, wasGlowing, glowing, receiver);
        if (oldColor != null && oldColor != Color.NONE/*We never add to NONE, so no need to remove*/) {
            sendTeamPacket(entity, oldColor/*use the old color to remove the player from its team*/, false, false, tagVisibility, push, receiver);
        }
        if (glowing) {
            sendTeamPacket(entity, color, false, color != Color.NONE, tagVisibility, push, receiver);
        }
    }

    /**
     * Set the glowing-color of an entity
     *
     * @param entity   {@link Entity} to update
     * @param color    {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
     * @param receiver {@link Player} that will see the update
     */
    public static void setGlowing(Entity entity, Color color, Player receiver) {
        setGlowing(entity, color, "always", "always", receiver);
    }

    /**
     * Set the glowing-color of an entity
     *
     * @param entity   {@link Entity} to update
     * @param glowing  whether the entity is glowing or not
     * @param receiver {@link Player} that will see the update
     * @see #setGlowing(Entity, Color, Player)
     */
    public static void setGlowing(Entity entity, boolean glowing, Player receiver) {
        setGlowing(entity, glowing ? Color.NONE : null, receiver);
    }

    /**
     * Set the glowing-color of an entity
     *
     * @param entity    {@link Entity} to update
     * @param glowing   whether the entity is glowing or not
     * @param receivers Collection of {@link Player}s that will see the update
     * @see #setGlowing(Entity, Color, Player)
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
     * @param color     {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
     * @param receivers Collection of {@link Player}s that will see the update
     */
    public static void setGlowing(Entity entity, Color color, Collection<? extends Player> receivers) {
        for (Player receiver : receivers) {
            setGlowing(entity, color, receiver);
        }
    }

    /**
     * Set the glowing-color of an entity
     *
     * @param entities Collection of {@link Entity} to update
     * @param color    {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
     * @param receiver {@link Player} that will see the update
     */
    public static void setGlowing(Collection<? extends Entity> entities, Color color, Player receiver) {
        for (Entity entity : entities) {
            setGlowing(entity, color, receiver);
        }
    }

    /**
     * Set the glowing-color of an entity
     *
     * @param entities  Collection of {@link Entity} to update
     * @param color     {@link org.inventivetalent.glow.GlowAPI.Color} of the glow, or <code>null</code> to stop glowing
     * @param receivers Collection of {@link Player}s that will see the update
     */
    public static void setGlowing(Collection<? extends Entity> entities, Color color, Collection<? extends Player> receivers) {
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

    private static boolean isGlowing(UUID entity, Player receiver) {
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
                if (isGlowing(entity, receiver)) {return true;}
            }
        }
        return false;
    }

    /**
     * Get the glow-color of an entity
     *
     * @param entity   {@link Entity} to get the color for
     * @param receiver {@link Player} receiver of the color (as used in the setGlowing methods)
     * @return the {@link org.inventivetalent.glow.GlowAPI.Color}, or <code>null</code> if the entity doesn't appear glowing to the player
     */
    public static Color getGlowColor(Entity entity, Player receiver) {
        return getGlowColor(entity.getUniqueId(), receiver);
    }

    private static Color getGlowColor(UUID entityUniqueId, Player receiver) {
        if (!dataMap.containsKey(entityUniqueId)) {return null;}
        GlowData data = dataMap.get(entityUniqueId);
        return data.colorMap.get(receiver.getUniqueId());
    }

    protected static void sendGlowPacket(Entity entity, boolean wasGlowing, boolean glowing, Player receiver) {
        try {
            if (PacketPlayOutEntityMetadata == null) {
                PacketPlayOutEntityMetadata = NMS_CLASS_RESOLVER.resolve("network.protocol.game.PacketPlayOutEntityMetadata");
            }
            if (DataWatcher == null) {
                DataWatcher = NMS_CLASS_RESOLVER.resolve("network.syncher.DataWatcher");
            }
            if (DataWatcherItem == null) {
                DataWatcherItem = NMS_CLASS_RESOLVER.resolve("network.syncher.DataWatcher$Item");
            }
            if (Entity == null) {
                Entity = NMS_CLASS_RESOLVER.resolve("world.entity.Entity");
            }
            if (PacketPlayOutMetadataFieldResolver == null) {
                PacketPlayOutMetadataFieldResolver = new FieldResolver(PacketPlayOutEntityMetadata);
            }
            if (PacketPlayOutMetadataResolver == null) {
                PacketPlayOutMetadataResolver = new ConstructorResolver(PacketPlayOutEntityMetadata);
            }
            if (DataWatcherItemConstructorResolver == null) {
                DataWatcherItemConstructorResolver = new ConstructorResolver(DataWatcherItem);
            }
            if (EntityFieldResolver == null) {
                EntityFieldResolver = new FieldResolver(Entity);
            }
            if (DataWatcherMethodResolver == null) {
                DataWatcherMethodResolver = new MethodResolver(DataWatcher);
            }
            if (DataWatcherItemMethodResolver == null) {
                DataWatcherItemMethodResolver = new MethodResolver(DataWatcherItem);
            }
            if (EntityMethodResolver == null) {
                EntityMethodResolver = new MethodResolver(Entity);
            }
            if (DataWatcherFieldResolver == null) {
                DataWatcherFieldResolver = new FieldResolver(DataWatcher);
            }

            List list = new ArrayList();

            //Existing values
            Object dataWatcher = EntityMethodResolver.resolve("getDataWatcher", "ai").invoke(Minecraft.getHandle(entity));
            Class dataWatcherItemsType;
            if (isPaper || MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_18_R1)) {
                dataWatcherItemsType = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");
            } else {
                dataWatcherItemsType = Class.forName("org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.ints.Int2ObjectMap");
            }
            Map<Integer, Object> dataWatcherItems = (Map<Integer, Object>) DataWatcherFieldResolver.resolveByLastType(dataWatcherItemsType).get(dataWatcher);

            Object dataWatcherObject = org.inventivetalent.reflection.minecraft.DataWatcher.V1_9.ValueType.ENTITY_SHARED_FLAGS.getType();
            byte prev = (byte) (dataWatcherItems.isEmpty() ? 0 : DataWatcherItemMethodResolver.resolve("b").invoke(dataWatcherItems.get(0)));
            byte b = (byte) (glowing ? (prev | 1 << 6) : (prev & ~(1 << 6)));//6 = glowing index
            Object dataWatcherItem = DataWatcherItemConstructorResolver.resolveFirstConstructor().newInstance(dataWatcherObject, b);

            //The glowing item
            list.add(dataWatcherItem);

            Object packetMetadata = PacketPlayOutMetadataResolver
                    .resolve(new Class[]{int.class, DataWatcher, boolean.class})
                    .newInstance(-entity.getEntityId(), dataWatcher, true);
            List dataWatcherList = (List) PacketPlayOutMetadataFieldResolver.resolve("b").get(packetMetadata);
            dataWatcherList.clear();
            dataWatcherList.addAll(list);

            sendPacket(packetMetadata, receiver);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
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

    protected static void sendTeamPacket(Entity entity, Color color, boolean createNewTeam/*If true, we don't add any entities*/, boolean addEntity/*true->add the entity, false->remove the entity*/, String tagVisibility, String push, Player receiver) {
        try {
            if (PacketPlayOutScoreboardTeam == null) {
                PacketPlayOutScoreboardTeam = NMS_CLASS_RESOLVER.resolve("network.protocol.game.PacketPlayOutScoreboardTeam");
            }
            if (PacketScoreboardTeamResolver == null) {
                PacketScoreboardTeamResolver = new ConstructorResolver(PacketPlayOutScoreboardTeam);
            }
            if (PacketScoreboardTeamFieldResolver == null) {
                PacketScoreboardTeamFieldResolver = new FieldResolver(PacketPlayOutScoreboardTeam);
            }
            if (Scoreboard == null) {
                Scoreboard = NMS_CLASS_RESOLVER.resolve("world.scores.Scoreboard");
            }
            if (ScoreboardResolver == null) {
                ScoreboardResolver = new ConstructorResolver(Scoreboard);
            }
            if (ScoreboardTeam == null) {
                ScoreboardTeam = NMS_CLASS_RESOLVER.resolve("world.scores.ScoreboardTeam");
            }
            if (ScoreboardTeamResolver == null) {
                ScoreboardTeamResolver = new ConstructorResolver(ScoreboardTeam);
            }
            if (ScoreboardTeamMethodResolver == null) {
                ScoreboardTeamMethodResolver = new MethodResolver(ScoreboardTeam);
            }
            if (ChatComponentTextResolver == null && MinecraftVersion.VERSION.olderThan(Minecraft.Version.v1_19_R1)) {
                ChatComponentTextResolver = new ConstructorResolver(NMS_CLASS_RESOLVER.resolve("network.chat.ChatComponentText"));
            }
            if (IChatBaseComponentMethodResolver == null && MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_19_R1)) {
                IChatBaseComponentMethodResolver = new MethodResolver(NMS_CLASS_RESOLVER.resolve("network.chat.IChatBaseComponent"));
            }

            final int mode = (createNewTeam ? 0 : addEntity ? 3 : 4); //Mode (0 = create, 3 = add entity, 4 = remove entity)

            Object nms$ScoreboardTeam = null;
            Object packetScoreboardTeam = null;

            if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) {
                if (nms$Scoreboard == null) {
                    nms$Scoreboard = ScoreboardResolver.resolveFirstConstructor().newInstance();
                }
                nms$ScoreboardTeam = ScoreboardTeamResolver.resolveFirstConstructor().newInstance(nms$Scoreboard, color.getTeamName());
            } else {
                packetScoreboardTeam = PacketPlayOutScoreboardTeam.newInstance();
                PacketScoreboardTeamFieldResolver.resolve("i").set(packetScoreboardTeam, mode);//Mode
                PacketScoreboardTeamFieldResolver.resolve("a").set(packetScoreboardTeam, color.getTeamName());//Name
                PacketScoreboardTeamFieldResolver.resolve("e").set(packetScoreboardTeam, tagVisibility);//NameTag visibility
                PacketScoreboardTeamFieldResolver.resolve("f").set(packetScoreboardTeam, push);//Team-push
            }

            if (createNewTeam) {
                Object prefix;
                Object displayName;
                Object suffix;

                if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_19_R1)) {
                    Method aMethod = IChatBaseComponentMethodResolver.resolve(new ResolverQuery("b", String.class));
                    prefix = aMethod.invoke(null, "§" + color.colorCode);
                    displayName = aMethod.invoke(null, color.getTeamName());
                    suffix = aMethod.invoke(null, "");
                } else {
                    prefix = ChatComponentTextResolver.resolveFirstConstructor().newInstance("§" + color.colorCode);
                    displayName = ChatComponentTextResolver.resolveFirstConstructor().newInstance(color.getTeamName());
                    suffix = ChatComponentTextResolver.resolveFirstConstructor().newInstance("");
                }

                if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) {
                    if (PacketPlayOutScoreboardTeam$info == null) {
                        PacketPlayOutScoreboardTeam$info = NMS_CLASS_RESOLVER.resolve("network.protocol.game.PacketPlayOutScoreboardTeam$b", "network.protocol.game.PacketPlayOutScoreboardTeam$Parameters");
                    }
                    if (PacketScoreboardTeamInfoResolver == null) {
                        PacketScoreboardTeamInfoResolver = new ConstructorResolver(PacketPlayOutScoreboardTeam$info);
                    }

                    ScoreboardTeamMethodResolver.resolve(new ResolverQuery[]{
                            new ResolverQuery("setDisplayName"),
                            new ResolverQuery("a", NMS_CLASS_RESOLVER.resolve("network.chat.IChatBaseComponent")),
                    }).invoke(nms$ScoreboardTeam, displayName);
                    ScoreboardTeamMethodResolver.resolve(new ResolverQuery[]{
                            new ResolverQuery("setPrefix"),
                            new ResolverQuery("b", NMS_CLASS_RESOLVER.resolve("network.chat.IChatBaseComponent")),
                    }).invoke(nms$ScoreboardTeam, prefix);
                    ScoreboardTeamMethodResolver.resolve(new ResolverQuery[]{
                            new ResolverQuery("setSuffix"),
                            new ResolverQuery("c", NMS_CLASS_RESOLVER.resolve("network.chat.IChatBaseComponent")),
                    }).invoke(nms$ScoreboardTeam, suffix);
                    ScoreboardTeamMethodResolver.resolve(new ResolverQuery[]{
                            new ResolverQuery("setColor"),
                            new ResolverQuery("a", NMS_CLASS_RESOLVER.resolve("EnumChatFormat")),
                    }).invoke(nms$ScoreboardTeam, color.packetValue);

                    Object packetScoreboardTeamInfo = PacketScoreboardTeamInfoResolver.resolveFirstConstructor().newInstance(nms$ScoreboardTeam);
                    packetScoreboardTeam = PacketScoreboardTeamResolver.resolve(new Class[]{String.class, int.class, Optional.class, Collection.class}).newInstance(color.getTeamName(), mode, Optional.of(packetScoreboardTeamInfo), ImmutableList.of());
                } else {
                    PacketScoreboardTeamFieldResolver.resolve("g").set(packetScoreboardTeam, color.packetValue);//Color -> this is what we care about

                    PacketScoreboardTeamFieldResolver.resolve("c").set(packetScoreboardTeam, prefix);//prefix - for some reason this controls the color, even though there's the extra color value...
                    PacketScoreboardTeamFieldResolver.resolve("b").set(packetScoreboardTeam, displayName);//Display name
                    PacketScoreboardTeamFieldResolver.resolve("d").set(packetScoreboardTeam, suffix);//suffix
                    PacketScoreboardTeamFieldResolver.resolve("j").set(packetScoreboardTeam, 0);//Options - let's just ignore them for now
                }
            } else {
                /* Add/remove players */

                Collection<String> entitiesList;
                if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) {
                    if (scoreboardTeamEntityList == null) {
                        scoreboardTeamEntityList = Lists.newArrayList();
                    }
                    scoreboardTeamEntityList.clear();
                    entitiesList = scoreboardTeamEntityList;
                } else {
                    entitiesList = ((Collection<String>) PacketScoreboardTeamFieldResolver.resolve("h").get(packetScoreboardTeam));
                }

                if (entity instanceof OfflinePlayer) {//Players still use the name...
                    entitiesList.add(entity.getName());
                } else {
                    entitiesList.add(entity.getUniqueId().toString());
                }

                if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) {
                    packetScoreboardTeam = PacketScoreboardTeamResolver.resolve(new Class[]{String.class, int.class, Optional.class, Collection.class}).newInstance(color.getTeamName(), mode, Optional.empty(), entitiesList);
                }
            }

            sendPacket(packetScoreboardTeam, receiver);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void sendPacket(Object packet, Player p) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchFieldException, NoSuchMethodException {
        if (EntityPlayerFieldResolver == null) {
            EntityPlayerFieldResolver = new FieldResolver(NMS_CLASS_RESOLVER.resolve("server.level.EntityPlayer"));
        }
        if (PlayerConnectionMethodResolver == null) {
            PlayerConnectionMethodResolver = new MethodResolver(NMS_CLASS_RESOLVER.resolve("server.network.PlayerConnection"));
        }

        try {
            Object handle = Minecraft.getHandle(p);
            final Object connection;

            if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) { // even playerConnection got changed!
                connection = EntityPlayerFieldResolver.resolve("b").get(handle);
            } else {
                connection = EntityPlayerFieldResolver.resolve("playerConnection").get(handle);
            }

            PlayerConnectionMethodResolver.resolve(new ResolverQuery[]{
                    new ResolverQuery("sendPacket"),
                    new ResolverQuery("a", NMS_CLASS_RESOLVER.resolve("network.protocol.Packet"))
            }).invoke(connection, packet);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Team Colors
     */
    public enum Color {

        BLACK(0, "0"),
        DARK_BLUE(1, "1"),
        DARK_GREEN(2, "2"),
        DARK_AQUA(3, "3"),
        DARK_RED(4, "4"),
        DARK_PURPLE(5, "5"),
        GOLD(6, "6"),
        GRAY(7, "7"),
        DARK_GRAY(8, "8"),
        BLUE(9, "9"),
        GREEN(10, "a"),
        AQUA(11, "b"),
        RED(12, "c"),
        PURPLE(13, "d"),
        YELLOW(14, "e"),
        WHITE(15, "f"),
        NONE(-1, "");

        Object packetValue;
        String colorCode;

        Color(int packetValue, String colorCode) {
            try {
                if (EnumChatFormatResolver == null) {
                    EnumChatFormatResolver = new MethodResolver(NMS_CLASS_RESOLVER.resolve("EnumChatFormat"));
                }

                this.packetValue = EnumChatFormatResolver.resolve(new ResolverQuery("a", int.class)).invoke(null, packetValue);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException |
                     ClassNotFoundException e) {
                e.printStackTrace();
            }

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

    //This gets called either by #initAPI above or #initAPI in one of the requiring plugins
    public void init(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        entityById.remove(event.getPlayer().getEntityId());
    }

    @PacketOptions(forcePlayer = true)
    @Override
    public void onSend(SentPacket sentPacket) {
        if (!"PacketPlayOutEntityMetadata".equals(sentPacket.getPacketName())) return;
        if (PacketPlayOutEntityMetadata == null) return;
        if (PacketPlayOutMetadataFieldResolver == null) return;
        if (DataWatcherItem != null && DataWatcherItemFieldResolver == null) {
            DataWatcherItemFieldResolver = new FieldResolver(GlowAPI.DataWatcherItem);
        }

        Object rawPacket = sentPacket.getPacket();

        try {
            int a = (int) sentPacket.getPacketValue("a");
            if (a < 0) {//Our packet
                //Reset the ID and let it through
                sentPacket.setPacketValue("a", -a);
                return;
            }

            List dataWatcherItemsList = (List) PacketPlayOutMetadataFieldResolver.resolve("b").get(rawPacket);
            if (dataWatcherItemsList.size() <= 0) return;

            Object dataWatcherItem = dataWatcherItemsList.get(0);
            if (dataWatcherItem == null) return;

            Object dataWatcherItemAccessor = DataWatcherItemMethodResolver.resolve("a").invoke(dataWatcherItem);
            if (dataWatcherItemAccessor == null) return;

            if (DataWatcherItemAccessorFieldResolver == null) {
                DataWatcherItemAccessorFieldResolver = new FieldResolver(nmsClassResolver.resolve("network.syncher.DataWatcherObject"));
            }

            int id = (Integer) DataWatcherItemAccessorFieldResolver.resolve("a").get(dataWatcherItemAccessor);
            if (id != 0) return;

            int targetEntityId = (Integer) PacketPlayOutMetadataFieldResolver.resolve("a").get(rawPacket);
            UUID entityUniqueId = entityById.get(targetEntityId);
            if (entityUniqueId == null) return;

            byte dataWatcherItemValue = (byte) DataWatcherItemMethodResolver.resolve("b").invoke(dataWatcherItem);

            boolean internalGlowFlagValue = (dataWatcherItemValue & (1 << 6)) == (1 << 6);
            boolean currentGlowFlagValue = isGlowing(entityUniqueId, sentPacket.getPlayer());
            if (internalGlowFlagValue == currentGlowFlagValue) return;

            byte newDataWatcherItemValue = (byte) (currentGlowFlagValue ? dataWatcherItemValue | (1 << 6) : dataWatcherItemValue & ~(1 << 6));
            DataWatcherItemMethodResolver.resolve(
                    new ResolverQuery("a", Object.class)
            ).invoke(dataWatcherItem, newDataWatcherItemValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onReceive(ReceivedPacket receivedPacket) {
    }

    protected static NMSClassResolver nmsClassResolver = new NMSClassResolver();
    protected static OBCClassResolver obcClassResolver = new OBCClassResolver();

    private static Class<?> LevelEntityGetter;

    private static FieldResolver CraftWorldFieldResolver;
    private static FieldResolver WorldFieldResolver;
    private static FieldResolver WorldServerFieldResolver;
    private static MethodResolver IntHashMapMethodResolver;
    private static MethodResolver LevelEntityGetterMethodResolver;
    private static MethodResolver WorldServerMethodResolver;

    public static Entity getEntityById(World world, int entityId) {
        try {
            if (CraftWorldFieldResolver == null) {
                CraftWorldFieldResolver = new FieldResolver(obcClassResolver.resolve("CraftWorld"));
            }
            if (WorldFieldResolver == null) {
                WorldFieldResolver = new FieldResolver(nmsClassResolver.resolve("world.level.World"));
            }
            if (WorldServerFieldResolver == null) {
                WorldServerFieldResolver = new FieldResolver(nmsClassResolver.resolve("server.level.WorldServer"));
            }
            if (EntityMethodResolver == null) {
                EntityMethodResolver = new MethodResolver(nmsClassResolver.resolve("world.entity.Entity"));
            }
            if (WorldServerMethodResolver == null) {
                WorldServerMethodResolver = new MethodResolver(nmsClassResolver.resolve("server.level.WorldServer"));
            }

            Object nmsWorld = CraftWorldFieldResolver.resolve("world").get(world);
            Object entity;
            if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_18_R1)) {
                entity = world.getEntitiesByClass(ItemFrame.class).stream().filter(i -> i.getEntityId() == entityId).findFirst().orElse(null);
                if (entity != null) {
                    entity = Minecraft.getHandle(entity);
                }
            } else if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_17_R1)) {
                // no more entitiesById in 1.17+
                entity = WorldServerMethodResolver.resolve(new ResolverQuery[]{
                        new ResolverQuery("getEntity", int.class),
                        new ResolverQuery("a", int.class)
                }).invoke(nmsWorld, entityId);
            } else {
                Object entitiesById;
                // NOTE: this check can be false, if the v1_14_R1 doesn't exist (stupid java), i.e. in old ReflectionHelper versions
                if (MinecraftVersion.VERSION.newerThan(Minecraft.Version.v1_8_R1)
                        && MinecraftVersion.VERSION.olderThan(Minecraft.Version.v1_14_R1)) { /* seriously?! between 1.8 and 1.14 entitiesyId was moved to World */
                    entitiesById = WorldFieldResolver.resolveAccessor("entitiesById").get(nmsWorld);
                } else {
                    entitiesById = WorldServerFieldResolver.resolveAccessor("entitiesById").get(nmsWorld);
                }

                if (MinecraftVersion.VERSION.olderThan(Minecraft.Version.v1_14_R1)) {// < 1.14 uses IntHashMap
                    if (IntHashMapMethodResolver == null) {
                        IntHashMapMethodResolver = new MethodResolver(NMS_CLASS_RESOLVER.resolve("IntHashMap"));
                    }

                    entity = IntHashMapMethodResolver.resolve(new ResolverQuery("get", int.class)).invoke(entitiesById, entityId);
                } else {// > 1.14 uses Int2ObjectMap which implements Map
                    entity = ((Map) entitiesById).get(entityId);
                }
            }
            if (entity == null) {return null;}
            return (Entity) EntityMethodResolver.resolve("getBukkitEntity").invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GlowAPI() {
    }

}
