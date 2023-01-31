package ru.craftlogic.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.economy.EconomyManager;
import ru.craftlogic.api.event.player.PlayerTeleportRequestEvent;
import ru.craftlogic.api.permission.PermissionManager;
import ru.craftlogic.api.server.Server;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.text.TextString;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.api.world.CommandSender;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.MuteManager.Mute;
import ru.craftlogic.chat.common.commands.*;
import ru.craftlogic.common.command.CommandManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Collections.singleton;

public class ChatManager extends ConfigurableManager {
    private static final Logger LOGGER = LogManager.getLogger("ChatManager");

    private final Map<String, Channel> channels = new HashMap<>();
    private final Map<UUID, UUID> playerLastMessage = new HashMap<>();
    private final Map<String, Function<Player, Text<?, ?>>> argSuppliers = new HashMap<>();
    private final MuteManager muteManager;
    private final IgnoreManager ignoreManager;
    private boolean enabled;
    private boolean alone;

    public ChatManager(Server server, Path settingsDirectory) {
        super(server, settingsDirectory.resolve("chat.json"), LOGGER);
        this.muteManager = new MuteManager(this, settingsDirectory.resolve("chat/mutes.json"), LOGGER);
        this.ignoreManager = new IgnoreManager(this, settingsDirectory.resolve("chat/ignores.json"), LOGGER);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void addArgSupplier(String arg, Function<Player, Text<?, ?>> supplier) {
        this.argSuppliers.put(arg, supplier);
    }

    @Override
    protected String getModId() {
        return CraftChat.MOD_ID;
    }

    @Override
    protected void load(JsonObject config) {
        enabled = JsonUtils.getBoolean(config, "enabled", false);
        alone = JsonUtils.getBoolean(config, "alone", true);
        JsonObject channels = JsonUtils.getJsonObject(config, "channels", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : channels.entrySet()) {
            String id = entry.getKey();
            JsonObject value = (JsonObject) entry.getValue();
            this.channels.put(id, new Channel(id, value));
        }
        try {
            muteManager.load();
            ignoreManager.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unload() throws Exception {
        if (enabled) {
            super.unload();
        }
    }

    @Override
    protected void save(JsonObject config) {
        config.addProperty("enabled", enabled);
        config.addProperty("alone", alone);
        JsonObject channels = new JsonObject();
        for (Map.Entry<String, Channel> entry : this.channels.entrySet()) {
            channels.add(entry.getKey(), entry.getValue().toJson());
        }
        config.add("channels", channels);
        try {
            muteManager.save(true);
            ignoreManager.save(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerCommands(CommandManager commandManager) {
        commandManager.registerCommand(new CommandMessage());
        if (server.isDedicated()) {
            commandManager.registerCommand(new CommandMute());
            commandManager.registerCommand(new CommandReplyMessage());
            commandManager.registerCommand(new CommandUnmute());
            commandManager.registerCommand(new CommandChat());
            commandManager.registerCommand(new CommandIgnore());
            commandManager.registerCommand(new CommandUnignore());
            commandManager.registerCommand(new CommandTwinks());
        }
    }

    public boolean removeIgnore(OfflinePlayer player, OfflinePlayer target) {
        return ignoreManager.removeIgnore(player.getId(), target.getId());
    }

    public boolean addIgnore(OfflinePlayer player, OfflinePlayer target) {
        return ignoreManager.addIgnore(player.getId(), target.getId());
    }

    public boolean isIgnored(OfflinePlayer player, OfflinePlayer target) {
        return ignoreManager.isIgnored(player.getId(), target.getId());
    }

    public boolean removeMute(OfflinePlayer player) {
        return muteManager.removeMute(player.getId());
    }

    public boolean addMute(OfflinePlayer player, long expiration, String reason) {
        return muteManager.addMute(player.getId(), expiration, reason);
    }

    public Mute getMute(OfflinePlayer player) {
        return getMute(player.getId());
    }

    public Mute getMute(UUID id) {
        return muteManager.getMute(id);
    }


    public UUID getLastSender(UUID id) {
        return playerLastMessage.get(id);
    }

    public void setLastSender(UUID player, UUID lastPlayer) {
        playerLastMessage.put(player, lastPlayer);
    }

    private Channel findMatchingChannel(String message) {
        for (Channel channel : this.channels.values()) {
            if (channel.test(message)) {
                return channel;
            }
        }
        return null;
    }

    public Text<?, ?> getTwinksInfo(GameProfile profile) {
        PropertyMap properties = profile.getProperties();
        Collection<Property> twinks = properties.get("twinks");
        if (twinks != null && !twinks.isEmpty()) {
            Text<?, ?> lines = Text.string();
            Property p = twinks.iterator().next();
            String v = p.getValue();
            if (!v.isEmpty()) {
                String raw = new String(Base64.getDecoder().decode(v));
                JsonArray array = GSON.fromJson(raw, JsonArray.class);
                int i = 0;
                for (JsonElement e : array) {
                    JsonObject t = e.getAsJsonObject();
                    String username = t.get("username").getAsString();
                    UUID uuid = UUID.fromString(t.get("uuid").getAsString());
                    UUID machineId = UUID.fromString(t.get("machineId").getAsString());
                    UUID userId = UUID.fromString(t.get("userId").getAsString());
                    int offences = t.get("offences").getAsInt();
                    long bannedUntil = t.get("banned").getAsJsonObject().get("secs_since_epoch").getAsLong();
                    boolean banned = System.currentTimeMillis() / 1000L < bannedUntil;
                    if (!uuid.equals(profile.getId())) {
                        if (i > 0) {
                            lines.append(Text.string(", ").yellow());
                        }
                        if (offences > 0) {
                            lines.append(Text.string(username).color(banned ? TextFormatting.RED : TextFormatting.GOLD)
                                .appendText(" (").yellow()
                                .appendText(String.valueOf(offences)).red()
                                .appendText(")").yellow()
                            );
                        } else {
                            lines.append(Text.string(username).gold());
                        }
                        i++;
                    }
                }
                if (i > 0) {
                    return lines;
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        GameProfile profile = event.player.getGameProfile();
        Text<?, ?> lines = getTwinksInfo(profile);
        if (lines != null) {
            Text<?, ?> warning = Text.translation("chat.twinks").yellow()
                .arg(profile.getName(), Text::gold);
            for (Player player : server.getPlayerManager().getAllOnline()) {
                if (player.hasPermission("chat.admin.twinks")) {
                    player.sendMessage(warning);
                    player.sendMessage(lines);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTeleportRequest(PlayerTeleportRequestEvent event) {
        if (!enabled) {
            return;
        }
        if (isIgnored(event.target, event.player)) {
            event.setCanceled(true);
            event.player.sendMessage(Text.translation("chat.teleport.ignored").red());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatMessage(ServerChatEvent event) {
        if (!enabled) {
            return;
        }
        event.setCanceled(true);
        String message = event.getMessage();
        String username = event.getUsername();
        Player player = Player.from(event.getPlayer());
        if (player == null) {
            return;
        }
        GameProfile profile = player.getProfile();

        Mute mute = this.muteManager.getMute(profile.getId());

        if (mute != null) {
            player.sendMessage(Text.translation("chat.muted").red());
            return;
        }

        Location senderLocation = player.getLocation();
        PermissionManager permissionManager = this.server.getPermissionManager();
        EconomyManager economyManager = this.server.getEconomyManager();
        String color = permissionManager.getPermissionMetadata(profile, "color");
        String prefix = permissionManager.getPermissionMetadata(profile, "prefix");
        String suffix = permissionManager.getPermissionMetadata(profile, "suffix");
        String tooltip = permissionManager.getPermissionMetadata(profile, "tooltip");
        Channel channel = this.findMatchingChannel(message);

        if (channel != null) {
            if (channel.permission == null || channel.permission.send == null || permissionManager.hasPermission(profile, channel.permission.send)) {
                if (channel.price != null && economyManager.isEnabled()) {
                    float price = channel.price.calculate(message.substring(channel.symbol.length()));
                    float balance = economyManager.getBalance(player);
                    if (balance >= price) {
                        economyManager.setBalance(player, balance - price);
                    } else {
                        player.sendMessage(Text.translation("chat.money").red().arg(price - balance, Text::darkRed));
                        return;
                    }
                }

                List<CommandSender> receivers = new ArrayList<>();
                List<CommandSender> spyReceivers = new ArrayList<>();
                for (Player p : this.server.getPlayerManager().getAllOnline()) {
                    if (!p.getId().equals(player.getId()) && (channel.permission == null || channel.permission.receive == null || p.hasPermission(channel.permission.receive))) {
                        if (channel.range == 0 || p.getLocation().distance(senderLocation) <= channel.range) {
                            receivers.add(p);
                        } else if (channel.permission != null && channel.permission.spy != null
                                && p.hasPermission(channel.permission.spy)) {
                            spyReceivers.add(p);
                        }
                    }
                }

                Map<String, Text<?, ?>> args = new HashMap<>();
                TextString u = Text.string(username).suggestCommand("/w " + username);
                if (tooltip != null && !tooltip.isEmpty()) {
                    u.hoverText(tooltip.replace('&', '\u00a7'));
                }
                if (color != null && !color.isEmpty()) {
                    u.color(findColor(color));
                }
                args.put("username", u);
                args.put("message", Text.string(message.substring(channel.symbol.length())).color(channel.color));
                args.put("prefix", prefix != null && !prefix.isEmpty() ? Text.string(prefix.replace('&', '\u00a7')) : Text.string());
                args.put("suffix", suffix != null && !suffix.isEmpty() ? Text.string(suffix.replace('&', '\u00a7')) : Text.string());

                for (Map.Entry<String, Function<Player, Text<?, ?>>> entry : this.argSuppliers.entrySet()) {
                    args.put(entry.getKey(), entry.getValue().apply(player));
                }

                Map<String, Text<?, ?>> ignoreArgs = new HashMap<>(args);
                ignoreArgs.put("message", Text.translation("chat.ignored").italic().color(channel.color));
                Text<?, ?> msg = channel.format(args);
                Text<?, ?> ignoreMsg = channel.format(ignoreArgs);

                this.server.sendMessage(msg);

                if (receivers.isEmpty() && alone) {
                    player.sendMessage(Text.translation("chat.alone").yellow());
                } else {
                    player.sendMessage(msg);
                    for (CommandSender receiver : receivers) {
                        if (receiver instanceof Player && ignoreManager.isIgnored(((Player) receiver).getId(), player.getId())) {
                            receiver.sendMessage(ignoreMsg);
                        } else {
                            receiver.sendMessage(msg);
                        }
                    }
                }
                for (CommandSender receiver : spyReceivers) {
                    receiver.sendMessage(Text.translation("chat.spy").gray().arg(msg));
                }
            } else {
                player.sendMessage(Text.translation("chat.permission").red().arg(channel.id, Text::darkRed));
            }
        }
    }

    private TextFormatting findColor(String color) {
        String formatted = color.replace('&', '\u00a7');
        for (TextFormatting fmt : TextFormatting.values()) {
            if (fmt.toString().equals(formatted)) {
                return fmt;
            }
        }
        return TextFormatting.RESET;
    }

    private static class Channel implements Predicate<String> {
        private final String id;
        private final String symbol;
        private final String format;
        private final TextFormatting color;
        private final Price price;
        private final Permission permission;
        private final int range;

        public Channel(String id, JsonObject data) {
            this.id = id;
            this.symbol = JsonUtils.getString(data, "symbol", "");
            Validate.isTrue(this.symbol.length() <= 1, "Channel symbol must be single character!");
            this.color = data.has("color") ? TextFormatting.getValueByName(JsonUtils.getString(data, "color")) : TextFormatting.RESET;
            this.format = JsonUtils.getString(data, "format", "{prefix}{username}{suffix}: {message}");
            this.price = data.has("price") ? new Price(data.get("price")) : null;
            this.permission = data.has("permission") ? new Permission(data.get("permission")) : null;
            this.range = JsonUtils.getInt(data, "range", 0);
            Validate.isTrue(this.range >= 0, "Chat range cannot be negative!");
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean test(String message) {
            return this.symbol.isEmpty() || message.startsWith(this.symbol);
        }

        public Text<?, ?> format(Map<String, Text<?, ?>> args) {
            Text<?, ?> msg = Text.string();
            String format = this.format;
            int sdx;
            while ((sdx = format.indexOf('{')) >= 0) {
                if (sdx > 0) {
                    msg.appendText(format.substring(0, sdx));
                }
                format = format.substring(sdx + 1);
                int edx = format.indexOf('}');
                if (edx >= 0) {
                    String var = format.substring(0, edx);
                    format = format.substring(edx + 1);
                    Text<?, ?> arg = args.get(var);
                    if (arg != null) {
                        msg.append(arg);
                        if (format.length() > 0 && !format.contains("{")) {
                            msg.appendText(format);
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown argument in channel '" + this.id + "': " + var);
                    }
                } else if (sdx > 0) {
                    throw new IllegalArgumentException("Unclosed curly bracket in channel '" + this.id + "' format at " + sdx);
                } else {
                    msg.appendText(format);
                }
            }
            return msg;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("symbol", this.symbol);
            if (this.color != TextFormatting.RESET) {
                obj.addProperty("color", this.color.name().toLowerCase());
            }
            if (!this.format.isEmpty()) {
                obj.addProperty("format", this.format);
            }
            if (this.price != null) {
                obj.add("price", this.price.toJson());
            }
            if (this.permission != null) {
                obj.add("permission", this.permission.toJson());
            }
            if (this.range != 0) {
                obj.addProperty("range", this.range);
            }
            return obj;
        }
    }

    private static class Price {
        private final PriceType type;
        private final int value;
        private final boolean shortened;

        public Price(JsonElement data) {
            if (data.isJsonPrimitive()) {
                this.type = PriceType.TOTAL;
                this.value = data.getAsNumber().intValue();
                this.shortened = true;
            } else {
                this.type = PriceType.valueOf(JsonUtils.getString(data, "type").toUpperCase());
                this.value = JsonUtils.getInt(data, "value");
                this.shortened = false;
            }
        }

        public JsonElement toJson() {
            if (this.shortened) {
                return new JsonPrimitive(this.value);
            } else {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", this.type.name().toLowerCase());
                obj.addProperty("value", this.value);
                return obj;
            }
        }

        public float calculate(String message) {
            switch (this.type) {
                case TOTAL:
                    return this.value;
                case PER_SYMBOL:
                    return this.value * message.length();
            }
            return 0;
        }

        public enum PriceType {
            TOTAL,
            PER_SYMBOL
        }
    }

    private static class Permission {
        private final String send, receive, spy;
        private final boolean shortened;

        private Permission(JsonElement data) {
            if (data.isJsonPrimitive()) {
                this.send = this.receive = data.getAsString();
                this.spy = null;
                this.shortened = true;
            } else {
                JsonObject obj = data.getAsJsonObject();
                this.send = obj.has("send") ? JsonUtils.getString(obj, "send") : null;
                this.receive = obj.has("receive") ? JsonUtils.getString(obj, "receive") : null;
                this.spy = obj.has("spy") ? JsonUtils.getString(obj, "spy") : null;
                this.shortened = false;
            }
        }

        public JsonElement toJson() {
            if (this.shortened) {
                return new JsonPrimitive(this.send);
            } else {
                JsonObject obj = new JsonObject();
                if (send != null) {
                    obj.addProperty("send", this.send);
                }
                if (receive != null) {
                    obj.addProperty("receive", this.receive);
                }
                if (spy != null) {
                    obj.addProperty("spy", this.spy);
                }
                return obj;
            }
        }
    }
}
