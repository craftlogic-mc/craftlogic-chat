package ru.craftlogic.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.util.ConfigurableManager;

import java.nio.file.Path;
import java.util.*;

public class IgnoreManager extends ConfigurableManager {
    final Map<UUID, Set<UUID>> ignores = new HashMap<>();

    public IgnoreManager(ChatManager chatManager, Path configFile, Logger logger) {
        super(chatManager.getServer(), configFile, logger);
    }

    @Override
    protected void load(JsonObject config) {
        ignores.clear();
        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            UUID id = UUID.fromString(entry.getKey());
            JsonArray entries = entry.getValue().getAsJsonArray();
            Set<UUID> ignores = new HashSet<>();
            for (JsonElement e : entries) {
                ignores.add(UUID.fromString(e.getAsString()));
            }
            this.ignores.put(id, ignores);
        }
    }

    @Override
    protected void save(JsonObject config) {
        for (Map.Entry<UUID, Set<UUID>> entry : ignores.entrySet()) {
            Set<UUID> ignores = entry.getValue();
            JsonArray entries = new JsonArray();
            for (UUID i : ignores) {
                entries.add(i.toString());
            }
            config.add(entry.getKey().toString(), entries);
        }
    }

    public boolean removeIgnore(UUID id, UUID target) {
        Set<UUID> ignores = this.ignores.get(id);
        if (ignores != null && ignores.remove(target)) {
            setDirty(true);
            return true;
        } else {
            return false;
        }
    }

    public boolean addIgnore(UUID id, UUID target) {
        Set<UUID> ignores = this.ignores.computeIfAbsent(id, i -> new HashSet<>());
        if (ignores.contains(target)) {
            return false;
        } else {
            ignores.add(target);
            setDirty(true);
            return true;
        }
    }

    public boolean isIgnored(UUID id, UUID target) {
        Set<UUID> ignores = this.ignores.get(id);
        return ignores != null && ignores.contains(target);
    }
}
