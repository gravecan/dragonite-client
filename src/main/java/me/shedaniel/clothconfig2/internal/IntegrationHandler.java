package me.shedaniel.clothconfig2.internal;

import java.io.IOException;
import java.io.RandomAccessFile;


public class IntegrationHandler {

    private static DiscordUser cachedUser = null;
    private static boolean attempted = false;

    public static class DiscordUser {
        public final String id;
        public final String username;
        public final String discriminator;
        public final String avatar;

        public DiscordUser(String id, String username, String discriminator, String avatar) {
            this.id = id;
            this.username = username;
            this.discriminator = discriminator;
            this.avatar = avatar;
        }

        public String getFullUsername() {
            if (discriminator == null || discriminator.equals("0") || discriminator.isEmpty()) {
                return username;
            }
            return username + "#" + discriminator;
        }

        @Override
        public String toString() {
            return "DiscordUser{id=" + id + ", username=" + getFullUsername() + "}";
        }
    }

    public static DiscordUser getUser() {
        if (cachedUser != null) {
            return cachedUser;
        }
        if (attempted) {
            return null;
        }
        attempted = true;
        try {
            cachedUser = fetchUser();
            if (cachedUser != null) {
                System.out.println("[IntegrationHandler] Connected: " + cachedUser.getFullUsername());
            }
        } catch (Exception e) {
            System.out.println("[IntegrationHandler] Discord not running or connection failed: " + e.getMessage());
        }
        return cachedUser;
    }

    public static void requireConnected() throws Exception {
        if (!BuildFingerprint.isReleaseBuild()) {
            return;
        }
        if (getUser() != null) {
            return;
        }
        throw new Exception(
                BuildFingerprint.decrypt("1b362c3c302d3b7f3b3a2c342b302f7f322a2c2b7f3d3a7f2d2a31313631387f3d3a39302d3a7f26302a7f333e2a313c377f1236313a3c2d3e392b7f771b2d3e383031362b3a7f3d36313b2c7f26302a2d7f33363c3a312c3a7f2b307f1b362c3c302d3b7671"));
    }

    private static DiscordUser fetchUser() throws IOException {
        RandomAccessFile pipe = DiscordIpc.openPipe();
        try {
            DiscordIpc.handshake(pipe, DiscordIpc.CLIENT_ID);
            for (int i = 0; i < 24; i++) {
                String json = DiscordIpc.readFrame(pipe);
                if (json == null) {
                    break;
                }
                DiscordUser user = parseUserJson(json);
                if (user != null) {
                    return user;
                }
            }
            return null;
        } finally {
            pipe.close();
        }
    }

    private static DiscordUser parseUserJson(String json) {
        if (json == null || !json.contains("username")) {
            return null;
        }
        String id = extractString(json, "\"id\"");
        String username = extractString(json, "\"username\"");
        String discriminator = extractString(json, "\"discriminator\"");
        String avatar = extractString(json, "\"avatar\"");
        if (id == null || username == null) {
            return null;
        }
        return new DiscordUser(id, username, discriminator, avatar);
    }

    private static String extractString(String json, String key) {
        String search = key + ":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            int alt = json.indexOf(key + ": \"");
            if (alt < 0) {
                return null;
            }
            start = alt + key.length() + 3;
        } else {
            start += search.length();
        }
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
}
