/*
 *  PacketWrapper - Contains wrappers for each packet in Minecraft.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package org.inventivetalent.glow.packetwrappers;

import java.util.Arrays;
import java.util.Collection;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WrapperPlayServerScoreboardTeam extends AbstractPacket {
    public static final PacketType TYPE = PacketType.Play.Server.SCOREBOARD_TEAM;

    public static final Class<?> EnumChatFormat = MinecraftReflection.getMinecraftClass("EnumChatFormat");

    private static final byte ALLOW_FRIENDLY_FIRE = 0x01;
    private static final byte CAN_SEE_FRIENDLY_INVISIBLE = 0x02;

    /**
     * Enumeration of all the known packet modes.
     * 
     * @author Kristian
     */
    @SuppressWarnings("unused")
    public enum Modes {
        TEAM_CREATED(0),
        TEAM_REMOVED(1),
        TEAM_UPDATED(2),
        PLAYERS_ADDED(3),
        PLAYERS_REMOVED(4);

        final int packetMode;

        @Nullable
        public static Modes valueOf(int packetMode) {
            return Arrays.stream(Modes.values())
                .filter(modes -> modes.packetMode == packetMode)
                .findAny()
                .orElse(null);
        }

        Modes(int packetMode) {
            this.packetMode = packetMode;
        }
    }

    @SuppressWarnings("unused")
    public enum TeamPush {
        ALWAYS("always"),
        PUSH_OTHER_TEAMS("pushOtherTeams"),
        PUSH_OWN_TEAM("pushOwnTeam"),
        NEVER("never");

        final String collisionRule;

        TeamPush(@NotNull String collisionRule) {
            this.collisionRule = collisionRule;
        }
    }

    @SuppressWarnings("unused")
    public enum NameTagVisibility {
        ALWAYS("always"),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams"),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam"),
        NEVER("never");

        final String nameTagVisibility;

        NameTagVisibility(@NotNull String nameTagVisibility) {
            this.nameTagVisibility = nameTagVisibility;
        }
    }

    @SuppressWarnings("unused")
    public WrapperPlayServerScoreboardTeam() {
        super(new PacketContainer(TYPE), TYPE);
        handle.getModifier().writeDefaults();
    }

    @SuppressWarnings("unused")
    public WrapperPlayServerScoreboardTeam(@NotNull PacketContainer packet) {
        super(packet, TYPE);
    }
    
    /**
     * Retrieve an unique name for the team. (Shared with scoreboard)..
     * @return The current Team Name
    */
    @NotNull
    @SuppressWarnings("unused")
    public String getName() {
        return handle.getStrings().read(0);
    }
    
    /**
     * Set an unique name for the team. (Shared with scoreboard)..
     * @param value - new value.
    */
    @SuppressWarnings("unused")
    public void setName(@NotNull String value) {
        handle.getStrings().write(0, value);
    }
    
    /**
     * Retrieve the current packet {@link Modes}.
     * <p>
     * This determines whether or not team information is added or removed.
     * @return The current packet mode.
    */
    @Nullable
    @SuppressWarnings("unused")
    public Modes getPacketMode() {
        return Modes.valueOf(handle.getIntegers().read(0));
    }
    
    /**
     * Set the current packet {@link Modes}.
     * <p>
     * This determines whether or not team information is added or removed.
     * @param value - new value.
    */
    @SuppressWarnings("unused")
    public void setPacketMode(@NotNull Modes value) {
        handle.getIntegers().write(0, value.packetMode);
    }
    
    /**
     * Retrieve the team display name.
     * <p>
     * A team must be created or updated.
     * @return The current display name.
    */
    @NotNull
    @SuppressWarnings("unused")
    public String getTeamDisplayName() {
        return handle.getChatComponents().read(0).toString();
    }
    
    /**
     * Set the team display name.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
    */
    @SuppressWarnings("unused")
    public void setTeamDisplayName(@NotNull String value) {
        handle.getChatComponents().write(0, WrappedChatComponent.fromText(value));
    }
    
    /**
     * Retrieve the team prefix. This will be inserted before the name of each team member.
     * <p>
     * A team must be created or updated.
     * @return The current Team Prefix
    */
    @NotNull
    @SuppressWarnings("unused")
    public String getTeamPrefix() {
        return handle.getChatComponents().read(1).toString();
    }
    
    /**
     * Set the team prefix. This will be inserted before the name of each team member.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
    */
    @SuppressWarnings("unused")
    public void setTeamPrefix(@NotNull String value) {
        handle.getChatComponents().write(1, WrappedChatComponent.fromText(value));
    }
    /**
     * Retrieve the team suffix. This will be inserted after the name of each team member.
     * <p>
     * A team must be created or updated.
     * @return The current Team Suffix
     */
    @NotNull
    @SuppressWarnings("unused")
    public String getTeamSuffix() {
        return handle.getChatComponents().read(2).toString();
    }

    /**
     * Set the team suffix. This will be inserted after the name of each team member.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
     */
    @SuppressWarnings("unused")
    public void setTeamSuffix(@NotNull String value) {
        handle.getChatComponents().write(2, WrappedChatComponent.fromText(value));
    }

    /**
     * Retrieve whether or not friendly fire is enabled.
     * <p>
     * A team must be created or updated.
     * @return The current Friendly fire
     */
    @SuppressWarnings("unused")
    public boolean getAllowFriendlyFire() {
        return ((handle.getIntegers().read(1).byteValue() & ALLOW_FRIENDLY_FIRE) != 0);
    }

    /**
     * Set whether or not friendly fire is enabled.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
     */
    @SuppressWarnings("unused")
    public void setAllowFriendlyFire(boolean value) {
        int packOptionData = handle.getIntegers().read(1);
        packOptionData = value ? (packOptionData | ALLOW_FRIENDLY_FIRE) : (packOptionData & ~ALLOW_FRIENDLY_FIRE);
        handle.getIntegers().write(1, packOptionData);
    }

    /**
     * Retrieve whether or not friendly invisible can be seen.
     * <p>
     * A team must be created or updated.
     * @return The current Friendly fire
     */
    @SuppressWarnings("unused")
    public boolean getCanSeeFriendlyInvisible() {
        return ((handle.getIntegers().read(1).byteValue() & CAN_SEE_FRIENDLY_INVISIBLE) != 0);
    }

    /**
     * Set whether or not friendly invisible can be seen.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
     */
    @SuppressWarnings("unused")
    public void setCanSeeFriendlyInvisible(boolean value) {
        int packOptionData = handle.getIntegers().read(1);
        packOptionData = value ? (packOptionData | CAN_SEE_FRIENDLY_INVISIBLE) : (packOptionData & ~CAN_SEE_FRIENDLY_INVISIBLE);
        handle.getIntegers().write(1, packOptionData);
    }
    
    /**
     * Retrieve the list of entries.
     * <p>
     * Packet mode must be one of the following for this to be valid:
     * <ul>
     *  <li>{@link Modes#TEAM_CREATED}</li>
     *  <li>{@link Modes#PLAYERS_ADDED}</li>
     *  <li>{@link Modes#PLAYERS_REMOVED}</li>
     * </ul>
     * @return A list of entries.
    */
    @NotNull
    @SuppressWarnings("unchecked")
    public Collection<String> getEntries() {
        return handle.getSpecificModifier(Collection.class).read(0);
    }
    
    /**
     * Set the list of entries.
     * <p>
     * Packet mode must be one of the following for this to be valid:
     * <ul>
     *  <li>{@link Modes#TEAM_CREATED}</li>
     *  <li>{@link Modes#PLAYERS_ADDED}</li>
     *  <li>{@link Modes#PLAYERS_REMOVED}</li>
     * </ul>
     * @param players - A list of entries.
    */
    @SuppressWarnings("unused")
    public void setEntries(@NotNull Collection<String> players) {
    	handle.getSpecificModifier(Collection.class).write(0, players);
    }

    /**
     * Retrieve the color of a team
     * <p>
     * A team must be created or updated.
     * @return The current color
     */
    @NotNull
    @SuppressWarnings("unused")
    public ChatColor getTeamColor() {
        return handle.getEnumModifier(ChatColor.class, EnumChatFormat).read(0);
    }

    /**
     * Sets the color of a team.
     * <p>
     * A team must be created or updated.
     * @param value - new value.
     */
    @SuppressWarnings("unused")
    public void setTeamColor(@NotNull ChatColor value) {
        handle.getEnumModifier(ChatColor.class, EnumChatFormat).write(0, value);
    }

    @NotNull
    @SuppressWarnings("unused")
    public TeamPush getTeamPush() {
        return TeamPush.valueOf(handle.getStrings().read(1));
    }

    @SuppressWarnings("unused")
    public void setTeamPush(@NotNull TeamPush value) {
        handle.getStrings().write(1, value.toString());
    }

    @NotNull
    @SuppressWarnings("unused")
    public NameTagVisibility getNameTagVisibility() {
        return NameTagVisibility.valueOf(handle.getStrings().read(2));
    }

    @SuppressWarnings("unused")
    public void setNameTagVisibility(@NotNull NameTagVisibility value) {
        handle.getStrings().write(2, value.toString());
    }
}