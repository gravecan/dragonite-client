package me.shedaniel.clothconfig2.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Session-only config profiles (+ share codes). Nothing written to disk — clears on restart.
 */
public final class ConfigProfileStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String SHARE_PREFIX = "DN1:";
    private static final java.util.concurrent.ConcurrentHashMap<String, Profile> MEMORY =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class Profile {
        public String id;
        public String name;
        public long updated;
        public String author;
        public JsonObject snapshot;

        public Profile() {}

        public Profile(String id, String name, long updated, String author, JsonObject snapshot) {
            this.id = id;
            this.name = name;
            this.updated = updated;
            this.author = author;
            this.snapshot = snapshot;
        }
    }

    private ConfigProfileStore() {}

    public static List<Profile> listLocal() {
        List<Profile> out = new ArrayList<>(MEMORY.values());
        out.sort(Comparator.comparingLong((Profile p) -> p.updated).reversed());
        return out;
    }

    public static Profile saveCurrent(ConfigBuilderImpl manager, String name, String author) {
        Profile profile = new Profile(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sanitizeName(name),
                System.currentTimeMillis(),
                author == null || author.isBlank() ? "local" : author.trim(),
                capture(manager));
        write(profile);
        return profile;
    }

    public static void overwrite(Profile profile, ConfigBuilderImpl manager) {
        profile.snapshot = capture(manager);
        profile.updated = System.currentTimeMillis();
        write(profile);
    }

    public static void delete(String id) {
        if (id != null) {
            MEMORY.remove(id);
        }
    }

    public static boolean apply(ConfigBuilderImpl manager, Profile profile) {
        if (manager == null || profile == null || profile.snapshot == null) return false;
        applySnapshot(manager, profile.snapshot);
        manager.saveKeybinds();
        return true;
    }

    public static String toShareCode(Profile profile) {
        String json = GSON.toJson(profile);
        return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static Profile fromShareCode(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.regionMatches(true, 0, SHARE_PREFIX, 0, SHARE_PREFIX.length())) {
            value = value.substring(SHARE_PREFIX.length());
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            Profile profile = GSON.fromJson(new String(decoded, StandardCharsets.UTF_8), Profile.class);
            if (profile == null || profile.snapshot == null) return null;
            if (profile.id == null || profile.id.isBlank()) {
                profile.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
            if (profile.name == null || profile.name.isBlank()) profile.name = "Imported";
            profile.updated = System.currentTimeMillis();
            return profile;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Profile importShareCode(String raw) {
        Profile profile = fromShareCode(raw);
        if (profile != null) write(profile);
        return profile;
    }

    public static JsonObject capture(ConfigBuilderImpl manager) {
        JsonObject root = new JsonObject();
        root.addProperty("v", 1);
        JsonObject modules = new JsonObject();
        for (ConfigCategoryImpl module : manager.getModules()) {
            if (module == null || module.getName() == null) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("key", module.getKeybind());
            JsonObject settings = new JsonObject();
            for (AbstractFieldBuilder field : module.getSettings()) {
                if (field == null || field.getName() == null) continue;
                writeField(settings, field);
            }
            entry.add("settings", settings);
            modules.add(module.getName(), entry);
        }
        root.add("modules", modules);
        return root;
    }

    public static void applySnapshot(ConfigBuilderImpl manager, JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) return;
        JsonObject modules = root.getAsJsonObject("modules");
        for (ConfigCategoryImpl module : manager.getModules()) {
            if (module == null || module.getName() == null || !modules.has(module.getName())) continue;
            JsonObject entry = modules.getAsJsonObject(module.getName());
            if (entry.has("enabled")) module.setEnabled(entry.get("enabled").getAsBoolean());
            if (entry.has("key")) module.setKeybind(entry.get("key").getAsInt());
            if (entry.has("settings") && entry.get("settings").isJsonObject()) {
                JsonObject settings = entry.getAsJsonObject("settings");
                for (AbstractFieldBuilder field : module.getSettings()) {
                    if (field == null || field.getName() == null || !settings.has(field.getName())) continue;
                    readField(field, settings.get(field.getName()));
                }
            }
        }
    }

    private static void writeField(JsonObject settings, AbstractFieldBuilder field) {
        String key = field.getName();
        if (field instanceof BooleanToggleBuilder b) {
            settings.addProperty(key, b.get());
        } else if (field instanceof DoubleFieldBuilder d) {
            settings.addProperty(key, d.get());
        } else if (field instanceof RangeSliderBuilder r) {
            JsonObject range = new JsonObject();
            range.addProperty("min", r.getMinVal());
            range.addProperty("max", r.getMaxVal());
            settings.add(key, range);
        } else if (field instanceof EnumSelectorBuilder e) {
            settings.addProperty(key, e.get());
        } else if (field instanceof StringFieldBuilder s) {
            settings.addProperty(key, s.get());
        } else if (field instanceof ColorFieldBuilder c) {
            settings.addProperty(key, c.getARGB());
        }
    }

    private static void readField(AbstractFieldBuilder field, com.google.gson.JsonElement value) {
        try {
            if (field instanceof BooleanToggleBuilder b && value.isJsonPrimitive()) {
                b.set(value.getAsBoolean());
            } else if (field instanceof DoubleFieldBuilder d && value.isJsonPrimitive()) {
                d.set(value.getAsDouble());
            } else if (field instanceof RangeSliderBuilder r && value.isJsonObject()) {
                JsonObject o = value.getAsJsonObject();
                if (o.has("min")) r.setMinVal(o.get("min").getAsDouble());
                if (o.has("max")) r.setMaxVal(o.get("max").getAsDouble());
            } else if (field instanceof EnumSelectorBuilder e && value.isJsonPrimitive()) {
                e.set(value.getAsString());
            } else if (field instanceof StringFieldBuilder s && value.isJsonPrimitive()) {
                s.set(value.getAsString());
            } else if (field instanceof ColorFieldBuilder c && value.isJsonPrimitive()) {
                c.setARGB(value.getAsInt());
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void write(Profile profile) {
        if (profile == null || profile.id == null) {
            return;
        }
        MEMORY.put(profile.id, profile);
    }

    private static String sanitizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) value = "Config";
        if (value.length() > 32) value = value.substring(0, 32);
        return value;
    }
}
