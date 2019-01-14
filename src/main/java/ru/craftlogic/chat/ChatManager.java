package ru.craftlogic.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.economy.EconomyManager;
import ru.craftlogic.api.inventory.ContainerBase;
import ru.craftlogic.api.server.Server;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.api.world.CommandSender;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.chat.MuteManager.Mute;
import ru.craftlogic.common.command.CommandManager;
import ru.craftlogic.permissions.PermissionManager;
import ru.craftlogic.permissions.UserManager.User;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public class ChatManager extends ConfigurableManager {
    private static final Logger LOGGER = LogManager.getLogger("ChatManager");

    private final Map<String, Channel> channels = new HashMap<>();
    private final MuteManager muteManager;
    private boolean enabled;

    public ChatManager(Server server, Path settingsDirectory) {
        super(server, settingsDirectory.resolve("chat.json"), LOGGER);
        this.muteManager = new MuteManager(this, settingsDirectory.resolve("chat/mutes.json"), LOGGER);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    protected String getModId() {
        return CraftChat.MOD_ID;
    }

    @Override
    protected void load(JsonObject config) {
        this.enabled = JsonUtils.getBoolean(config, "enabled", false);
        JsonObject channels = JsonUtils.getJsonObject(config, "channels", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : channels.entrySet()) {
            String id = entry.getKey();
            JsonObject value = (JsonObject) entry.getValue();
            this.channels.put(id, new Channel(id, value));
        }
        try {
            this.muteManager.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void save(JsonObject config) {
        config.addProperty("enabled", this.enabled);
        JsonObject channels = new JsonObject();
        for (Map.Entry<String, Channel> entry : this.channels.entrySet()) {
            channels.add(entry.getKey(), entry.getValue().toJson());
        }
        config.add("channels", channels);
        try {
            this.muteManager.save(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerCommands(CommandManager commandManager) {
        commandManager.registerCommandContainer(ChatCommands.class);
    }

    public boolean removeMute(OfflinePlayer player) {
        return this.muteManager.removeMute(player.getId());
    }

    public boolean addMute(OfflinePlayer player, long expiration, String reason) {
        return this.muteManager.addMute(player.getId(), expiration, reason);
    }

    public Mute getMute(OfflinePlayer player) {
        return this.getMute(player.getId());
    }

    public Mute getMute(UUID id) {
        return this.muteManager.getMute(id);
    }

    private Channel findMatchingChannel(String message) {
        for (Channel channel : this.channels.values()) {
            if (channel.test(message)) {
                return channel;
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatMessage(ServerChatEvent event) {
        ContainerBase
        if (!this.enabled) {
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
        PermissionManager permissionManager = (PermissionManager) this.server.getPermissionManager();
        EconomyManager economyManager = this.server.getEconomyManager();
        User user = permissionManager.getUser(profile.getId());
        Channel channel = this.findMatchingChannel(message);

        if (channel != null) {
            if (channel.permission == null || user.hasPermissions(channel.permission.send)) {
                if (channel.price != null && economyManager.isEnabled()) {
                    float price = channel.price.calculate(message.substring(channel.prefix.length()));
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
                    if (!p.getId().equals(player.getId()) && (channel.permission == null || p.hasPermission(channel.permission.receive))) {
                        if (channel.range == 0 || p.getLocation().distance(senderLocation) <= channel.range) {
                            if (p.getGameMode() != GameType.SPECTATOR) {
                                receivers.add(p);
                            } else {
                                spyReceivers.add(p);
                            }
                        } else if (channel.permission != null && channel.permission.spy != null
                                && p.hasPermission(channel.permission.spy)) {

                            spyReceivers.add(p);
                        }
                    }
                }

                Text<?, ?> msg = channel.format(user, username, message);

                this.server.sendMessage(msg);

                if (receivers.isEmpty()) {
                    player.sendMessage(Text.translation("chat.alone").yellow());
                } else {
                    player.sendMessage(msg);
                    for (CommandSender receiver : receivers) {
                        receiver.sendMessage(msg);
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

    private static class Channel implements Predicate<String> {
        private final String id;
        private final String symbol;
        private final String prefix;
        private final TextFormatting color;
        private final Price price;
        private final Permission permission;
        private final int range;

        public Channel(String id, JsonObject data) {
            this.id = id;
            this.symbol = JsonUtils.getString(data, "symbol", "");
            Validate.isTrue(this.symbol.length() <= 1, "Channel symbol must be single character!");
            this.color = data.has("color") ? TextFormatting.getValueByName(JsonUtils.getString(data, "color")) : TextFormatting.RESET;
            this.prefix = JsonUtils.getString(data, "prefix", "");
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

        public Text<?, ?> format(User user, String username, String message) {
            String prefix = user.prefix().replace('&', '\u00a7');
            String suffix = user.suffix().replace('&', '\u00a7');
            Text<?, ?> msg = Text.string();
            if (!this.prefix.isEmpty()) {
                msg.appendText(this.prefix);
            }
            if (!prefix.isEmpty()) {
                msg.appendText(prefix);
                msg.appendText(" ");
            }
            Text<?, ?> name = Text.string(username);
            if (!suffix.isEmpty()) {
                name.hoverText(suffix);
            }
            if (!this.symbol.isEmpty()) {
                message = message.substring(this.symbol.length());
            }
            return msg.append(name)
                      .appendText(": ")
                      .appendText(message, m -> m.color(this.color))
                      .italic();
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("symbol", this.symbol);
            if (this.color != TextFormatting.RESET) {
                obj.addProperty("color", this.color.name().toLowerCase());
            }
            if (!this.prefix.isEmpty()) {
                obj.addProperty("prefix", this.prefix);
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
                this.send = JsonUtils.getString(obj, "send");
                this.receive = JsonUtils.getString(obj, "receive");
                this.spy = obj.has("spy") ? JsonUtils.getString(obj, "spy") : null;
                this.shortened = false;
            }
        }

        public JsonElement toJson() {
            if (this.shortened) {
                return new JsonPrimitive(this.send);
            } else {
                JsonObject obj = new JsonObject();
                obj.addProperty("send", this.send);
                obj.addProperty("receive", this.receive);
                return obj;
            }
        }
    }
}
