# GlowAPI
[![Build Status](https://travis-ci.com/metalshark/GlowAPI.svg?branch=master)](https://travis-ci.com/metalshark/GlowAPI)
[![Latest release](https://img.shields.io/github/release/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/releases/latest)
[![GitHub contributors](https://img.shields.io/github/contributors/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/graphs/contributors)
[![License](https://img.shields.io/github/license/metalshark/GlowAPI.svg)](https://github.com/metalshark/GlowAPI/blob/master/LICENSE)

This API allows you to change the glow-color of entities and players.

- [SpigotMC Page](https://www.spigotmc.org/resources/api-glowapi.19422/)
- [Website](https://inventivetalent.org/)
- [Donate](https://donation.inventivetalent.org/plugin/GlowAPI)

**Depends on [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/).**

Versions 1.4.11 and below depended on [PacketListenerAPI](https://www.spigotmc.org/resources/api-packetlistenerapi.2930/) instead.

API versions 1.4.11 and below are also compatible with [APIManager](https://www.spigotmc.org/resources/api-apimanager.19738/).

Version 1.4.7 is intended for 1.13+ only. For older MC versions, please use 1.4.6.

## Usage
```java
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
```
## Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.metalshark</groupId>
        <artifactId>GlowAPI</artifactId>
        <version>master-bfab7675d2-1</version>
    </dependency>
</dependencies>
```