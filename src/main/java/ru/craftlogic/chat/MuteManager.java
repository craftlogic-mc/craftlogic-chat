package ru.craftlogic.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.util.ConfigurableManager;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MuteManager extends ConfigurableManager {
    final Map<UUID, Mute> mutes = new HashMap<>();

    public MuteManager(ChatManager chatManager, Path configFile, Logger logger) {
        super(chatManager.getServer(), configFile, logger);
    }

    @Override
    protected void load(JsonObject mutes) {
        this.mutes.clear();
        for (Map.Entry<String, JsonElement> entry : mutes.entrySet()) {
            UUID id = UUID.fromString(entry.getKey());
            this.mutes.put(id, new Mute(id, entry.getValue().getAsJsonObject()));
        }
    }

    @Override
    protected void save(JsonObject mutes) {
        for (Map.Entry<UUID, Mute> entry : this.mutes.entrySet()) {
            Mute mute = entry.getValue();
            if (mute.expiration > System.currentTimeMillis()) {
                mutes.add(entry.getKey().toString(), mute.toJson());
            }
        }
    }

    public boolean removeMute(UUID id) {
        if (this.mutes.remove(id) != null) {
            this.setDirty(true);
            return true;
        } else {
            return false;
        }
    }

    public boolean addMute(UUID id, long expiration, String reason) {
        if (this.mutes.containsKey(id)) {
            return false;
        } else {
            this.mutes.put(id, new Mute(id, expiration, reason));
            this.setDirty(true);
            return true;
        }
    }

    public Mute getMute(UUID id) {
        Mute mute = this.mutes.get(id);
        if (mute != null && mute.expiration <= System.currentTimeMillis()) {
            this.mutes.remove(id);
            return null;
        }
        return mute;
    }

    public static class Mute {
        public final UUID target;
        public final long expiration;
        public final String reason;

        public Mute(UUID target, JsonObject data) {
            this(target, data.get("expiration").getAsLong(), JsonUtils.getString(data, "reason", null));
        }

        public Mute(UUID target, long expiration, @Nullable String reason) {
            this.target = target;
            this.expiration = expiration;
            this.reason = reason;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("expiration", this.expiration);
            if (this.reason != null) {
                obj.addProperty("reason", this.reason);
            }
            return obj;
        }
    }
}
