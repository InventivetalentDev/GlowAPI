# GlowAPI
[![Build Status](https://travis-ci.com/metalshark/GlowAPI.svg?branch=master)](https://travis-ci.com/metalshark/GlowAPI)
[![Latest release](https://img.shields.io/github/release/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/releases/latest)
[![GitHub contributors](https://img.shields.io/github/contributors/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/graphs/contributors)
[![License](https://img.shields.io/github/license/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/blob/master/LICENSE)

This API allows you to change the glow-color of entities and players.

- [SpigotMC Page](https://www.spigotmc.org/resources/api-glowapi-async.76644/)
- [Website](https://inventivetalent.org/)
- [Donate](https://donation.inventivetalent.org/plugin/GlowAPI)

**Depends on [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/).**

Versions 1.4.11 and below depended on [PacketListenerAPI](https://www.spigotmc.org/resources/api-packetlistenerapi.2930/) instead.

API versions 1.4.11 and below are also compatible with [APIManager](https://www.spigotmc.org/resources/api-apimanager.19738/).

Version 1.4.7 is intended for 1.13+ only. For older MC versions, please use 1.4.6.

## Usage
```java
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.inventivetalent.glow.GlowAPI;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {

        //Delay the update by a few ticks until the player is actually on the server
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {

            //Set the event's player glowing in DARK_AQUA for all online players
            GlowAPI.setGlowing(event.getPlayer(), GlowAPI.Color.DARK_AQUA, Bukkit.getOnlinePlayers());
            
        }, 10);

    }

}
```

```java
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;

public class MyClass {

    public static void myMethod(Entity entity, Player player) {
        if (GlowAPI.isGlowing(entity, player)) {
            System.out.println("The entity is glowing for the player.");
        }
    }

}
```

```java
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.inventivetalent.glow.GlowAPI;

public class MyClass {

    public static void myMethod(Collection<Entity> entities, Collection<Player> players) {
        if (GlowAPI.isGlowing(entity, player)) {
            CompleteableFuture<Void> future = GlowAPI.setGlowingAsync(event.getPlayer(), GlowAPI.Color.DARK_AQUA, Bukkit.getOnlinePlayers());

            // Do some other work while that executes in the background

            future.join(); //Wait for glowing to be set
        }
    }

}
```
## Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependencies>
    <dependency>
        <groupId>com.github.metalshark</groupId>
        <artifactId>GlowAPI</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```
