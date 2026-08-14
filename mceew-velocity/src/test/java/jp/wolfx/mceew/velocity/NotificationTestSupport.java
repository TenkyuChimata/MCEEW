package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

final class NotificationTestSupport {
    private NotificationTestSupport() {
    }

    static final class Environment implements InvocationHandler {
        private final TestVelocityApi.RecordingScheduler scheduler =
                new TestVelocityApi.RecordingScheduler();
        private final List<RecordingPlayer> players = new ArrayList<>();
        private final Map<String, RegisteredServer> servers = new LinkedHashMap<>();
        private final List<Component> consoleMessages = new ArrayList<>();
        private final ConsoleCommandSource console = commandSource(consoleMessages);
        private final ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class}, this);

        ProxyServer proxy() {
            return proxy;
        }

        TestVelocityApi.RecordingScheduler scheduler() {
            return scheduler;
        }

        List<Component> consoleMessages() {
            return consoleMessages;
        }

        void registerServer(String name) {
            servers.put(VelocityConfigLoader.normalizeName(name), registeredServer(name));
        }

        RecordingPlayer addPlayer(String username, String backend, Set<String> permissions) {
            return addPlayer(username, backend, permissionValues(permissions));
        }

        RecordingPlayer addPlayer(
                String username,
                String backend,
                Map<String, Tristate> permissions
        ) {
            RecordingPlayer player = new RecordingPlayer(
                    UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)),
                    username,
                    backend,
                    permissions);
            addPlayer(player);
            return player;
        }

        void addPlayer(RecordingPlayer player) {
            players.add(player);
            if (player.backend() != null) {
                registerServer(player.backend());
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            switch (method.getName()) {
                case "getScheduler":
                    return scheduler;
                case "getCommandManager":
                    return scheduler.commandManager().proxy();
                case "getAllPlayers":
                    List<Player> views = new ArrayList<>();
                    for (RecordingPlayer player : players) {
                        views.add(player.player());
                    }
                    return Collections.unmodifiableList(views);
                case "getPlayerCount":
                    return players.size();
                case "getServer":
                    return Optional.ofNullable(servers.get(
                            VelocityConfigLoader.normalizeName((String) arguments[0])));
                case "getAllServers":
                    return Collections.unmodifiableCollection(servers.values());
                case "getConsoleCommandSource":
                    return console;
                default:
                    return TestVelocityApi.defaultValue(method.getReturnType());
            }
        }
    }

    static final class RecordingPlayer implements InvocationHandler {
        private final UUID uuid;
        private final String username;
        private final Map<String, Tristate> permissions;
        private final Player player;
        private final List<String> permissionQueries = new ArrayList<>();
        private final List<Component> messages = new ArrayList<>();
        private final List<Title> titles = new ArrayList<>();
        private final List<Sound> sounds = new ArrayList<>();
        private String backend;
        private ProtocolVersion protocolVersion = ProtocolVersion.MINECRAFT_1_19_3;
        private RuntimeException sendMessageFailure;
        private RuntimeException currentServerFailure;

        RecordingPlayer(
                UUID uuid,
                String username,
                String backend,
                Set<String> permissions
        ) {
            this(uuid, username, backend, permissionValues(permissions));
        }

        RecordingPlayer(
                UUID uuid,
                String username,
                String backend,
                Map<String, Tristate> permissions
        ) {
            this.uuid = uuid;
            this.username = username;
            this.backend = backend;
            this.permissions = Map.copyOf(permissions);
            player = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(), new Class<?>[]{Player.class}, this);
        }

        Player player() {
            return player;
        }

        String backend() {
            return backend;
        }

        void backend(String backend) {
            this.backend = backend;
        }

        void protocolVersion(ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        void failSendMessage(RuntimeException failure) {
            sendMessageFailure = failure;
        }

        void failCurrentServer(RuntimeException failure) {
            currentServerFailure = failure;
        }

        List<String> permissionQueries() {
            return permissionQueries;
        }

        List<Component> messages() {
            return messages;
        }

        List<Title> titles() {
            return titles;
        }

        List<Sound> sounds() {
            return sounds;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            switch (method.getName()) {
                case "getUniqueId":
                    return uuid;
                case "getUsername":
                    return username;
                case "hasPermission":
                    String permission = (String) arguments[0];
                    permissionQueries.add(permission);
                    return permissionValue(permission).asBoolean();
                case "getPermissionValue":
                    String permissionNode = (String) arguments[0];
                    permissionQueries.add(permissionNode);
                    return permissionValue(permissionNode);
                case "getCurrentServer":
                    if (currentServerFailure != null) {
                        throw currentServerFailure;
                    }
                    return backend == null
                            ? Optional.empty()
                            : Optional.of(serverConnection(player, backend));
                case "getProtocolVersion":
                    return protocolVersion;
                case "sendMessage":
                    if (sendMessageFailure != null) {
                        throw sendMessageFailure;
                    }
                    messages.add((Component) arguments[0]);
                    return null;
                case "showTitle":
                    titles.add((Title) arguments[0]);
                    return null;
                case "playSound":
                    sounds.add((Sound) arguments[0]);
                    return null;
                default:
                    return TestVelocityApi.defaultValue(method.getReturnType());
            }
        }

        private Tristate permissionValue(String permission) {
            return permissions.getOrDefault(permission, Tristate.UNDEFINED);
        }
    }

    private static Map<String, Tristate> permissionValues(Set<String> permissions) {
        Map<String, Tristate> values = new LinkedHashMap<>();
        for (String permission : permissions) {
            values.put(permission, Tristate.TRUE);
        }
        return Map.copyOf(values);
    }

    private static ConsoleCommandSource commandSource(List<Component> messages) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if ("sendMessage".equals(method.getName())) {
                messages.add((Component) arguments[0]);
                return null;
            }
            if ("hasPermission".equals(method.getName())) {
                return true;
            }
            return TestVelocityApi.defaultValue(method.getReturnType());
        };
        return (ConsoleCommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSource.class}, handler);
    }

    private static ServerConnection serverConnection(Player player, String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        RegisteredServer server = registeredServer(name);
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            switch (method.getName()) {
                case "getServerInfo":
                    return info;
                case "getServer":
                    return server;
                case "getPlayer":
                    return player;
                case "getPreviousServer":
                    return Optional.empty();
                default:
                    return TestVelocityApi.defaultValue(method.getReturnType());
            }
        };
        return (ServerConnection) Proxy.newProxyInstance(
                ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class}, handler);
    }

    private static RegisteredServer registeredServer(String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if ("getServerInfo".equals(method.getName())) {
                return info;
            }
            if ("getPlayersConnected".equals(method.getName())) {
                return List.of();
            }
            return TestVelocityApi.defaultValue(method.getReturnType());
        };
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class}, handler);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        switch (method.getName()) {
            case "toString":
                return proxy.getClass().getInterfaces()[0].getSimpleName() + "TestProxy";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == arguments[0];
            default:
                throw new UnsupportedOperationException(method.toString());
        }
    }
}
