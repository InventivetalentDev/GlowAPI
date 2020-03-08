# GlowAPI
This API allows you to change the glow-color of entities and players.

- [SpigotMC Page](https://www.spigotmc.org/resources/api-glowapi.19422/)
- [Website](https://inventivetalent.org/)
- [Donate](https://donation.inventivetalent.org/plugin/GlowAPI)

**Depends on [PacketListenerAPI](https://www.spigotmc.org/resources/api-packetlistenerapi.2930/)**

Version 1.4.7 is intended for 1.13+ only. For older MC versions, please use 1.4.6.

## Usage
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        //Delay the update by a few ticks until the player is actually on the server
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                //Set the event's player glowing in DARK_AQUA for all online players
                GlowAPI.setGlowing(event.getPlayer(), GlowAPI.Color.DARK_AQUA, Bukkit.getOnlinePlayers());
            }
        }, 10);
    }

## Maven
    <repositories>
      <repository>
        <id>inventive-repo</id>
        <url>https://repo.inventivetalent.org/content/groups/public/</url>
      </repository>
    </repositories>

    <dependencies>
      <dependency>
        <groupId>org.inventivetalent</groupId>
        <artifactId>glowapi</artifactId>
        <version>1.4.11-SNAPSHOT</version>
      </dependency>
    </dependencies>
    
## API Manager
This API is compatible with [APIManager](https://www.spigotmc.org/resources/api-apimanager.19738/).
