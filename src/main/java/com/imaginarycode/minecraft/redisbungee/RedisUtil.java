package com.imaginarycode.minecraft.redisbungee;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@VisibleForTesting
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisUtil {
    protected static void createPlayer(ProxiedPlayer player, Pipeline pipeline, boolean fireEvent) {
        createPlayer(player.getPendingConnection(), pipeline, fireEvent);
        if (player.getServer() != null)
            pipeline.hset("player:" + player.getUniqueId().toString(), "server", player.getServer().getInfo().getName());
    }

    protected static void createPlayer(PendingConnection connection, Pipeline pipeline, boolean fireEvent) {
        Map<String, String> data = new HashMap<>(4);
        data.put("online", "0");
        data.put("ip", connection.getAddress().getAddress().getHostAddress());
        data.put("proxy", RedisBungee.getConfiguration().getId());

        if (!connection.isOnlineMode()) {
            pipeline.sadd("proxy:" + RedisBungee.getApi().getServerId() + ":all", connection.getName().toLowerCase());
        }

        pipeline.sadd("proxy:" + RedisBungee.getApi().getServerId() + ":usersOnline", connection.getUniqueId().toString());
        pipeline.hmset("player:" + connection.getUniqueId().toString(), data);

        if (fireEvent) {
            pipeline.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                    connection.getUniqueId(), DataManager.DataManagerMessage.Action.JOIN,
                    new DataManager.LoginPayload(connection.getAddress().getAddress()))));
        }
    }

    public static void cleanUpPlayer(String uuid, Jedis rsc, boolean online) {
        RedisBungeeAPI api = RedisBungee.getApi();
        String server = api.getServerId();
        rsc.srem("proxy:" + server + ":usersOnline", uuid);
        if (!online) {
            String name = api.getNameFromUuid(UUID.fromString(uuid), false);
            if (name != null) {
                rsc.srem("proxy:" + server + ":all", name.toLowerCase());
            }
        }
        rsc.hdel("player:" + uuid, "server", "ip", "proxy");
        long timestamp = System.currentTimeMillis();
        rsc.hset("player:" + uuid, "online", String.valueOf(timestamp));
        rsc.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                UUID.fromString(uuid), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(timestamp))));
    }

    public static void cleanUpPlayer(ProxiedPlayer player, Pipeline pipe) {
        pipe.srem("proxy:" + RedisBungee.getApi().getServerId() + ":usersOnline", player.getUniqueId().toString());
        if (!player.getPendingConnection().isOnlineMode()) {
            pipe.srem("proxy:" + RedisBungee.getApi().getServerId() + ":all", player.getName().toLowerCase());
        }
        pipe.hdel("player:" + player, "server", "ip", "proxy");
        long time = System.currentTimeMillis();
        pipe.hset("player:" + player, "online", String.valueOf(time));
        pipe.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                player.getUniqueId(), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(time))));
    }

    public static boolean canUseLua(String redisVersion) {
        // Need to use >=2.6 to use Lua optimizations.
        String[] args = redisVersion.split("\\.");

        if (args.length < 2) {
            return false;
        }

        int major = Integer.parseInt(args[0]);
        int minor = Integer.parseInt(args[1]);

        return major >= 3 || (major == 2 && minor >= 6);
    }
}
