package com.imaginarycode.minecraft.redisbungee;

import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import protocolsupport.api.events.PocketServerInfoEvent;

public class PSPEPingHandler implements Listener {

    @EventHandler
    public void handle(PocketServerInfoEvent event) {
        int cnt = RedisBungee.getApi().getPlayerCount();
        event.setOnline(cnt);
    }

    static void bind(Plugin plugin) {
        try {
            Class<?> evt = PSPEPingHandler.class.getClassLoader().loadClass("protocolsupport.api.events.PocketServerInfoEvent");
            if (evt == null) {
                return;
            }
            plugin.getProxy().getPluginManager().registerListener(plugin, new PSPEPingHandler());
        } catch (ClassNotFoundException ignored) {
        }
    }
}
