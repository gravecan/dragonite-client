package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.FriendListFieldBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class FriendManager extends ConfigCategoryImpl {
    private static FriendManager INSTANCE;

    private static String _s(int[] d, int k) {
        char[] c = new char[d.length];
        for (int i = 0; i < d.length; i++) c[i] = (char)(d[i] ^ k);
        return new String(c);
    }

    private final BooleanToggleBuilder chatNotify;
    private final FriendListFieldBuilder friendList;
    private final Set<String> friends = new LinkedHashSet<>();
    private final Map<String, String> friendDisplayNames = new LinkedHashMap<>();
    private final Set<UUID> friendUUIDs = ConcurrentHashMap.newKeySet();
    private long lastToggleTime = 0;

    public FriendManager() {
        super("Toggle Friends", "Shift right-click a player to add or remove them from your friend list", Cat.MISC);
        INSTANCE = this;
        chatNotify = new BooleanToggleBuilder("Chat Notify", "Show messages when you add or remove someone", true);
        friendList = new FriendListFieldBuilder("Friend list", this::friendNamesForGui, this::removeFriend);
        addSetting(chatNotify);
        addSetting(friendList);
        setEnabled(false);
        friends.addAll(PersistenceHelper.loadFriends());
        for (String key : friends) {
            friendDisplayNames.putIfAbsent(key, key);
        }
    }

    public static FriendManager getInstance() {
        return INSTANCE;
    }

    
    public static boolean isCombatExempt(PlayerEntity player) {
        FriendManager fm = INSTANCE;
        return fm != null && fm.isFriend(player);
    }

    public void tick() {
        cacheOnlineUuids();
    }

    public boolean tryUseToggle(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return false;
        }
        if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr)) {
            return false;
        }
        if (!(ehr.getEntity() instanceof PlayerEntity target) || target == mc.player) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < (250 * 2)) {
            return true;
        }
        lastToggleTime = now;
        toggleFriend(target);
        return true;
    }

    public void addFriend(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String key = name.toLowerCase().trim();
        if (!friends.add(key)) {
            return;
        }
        friendDisplayNames.put(key, name.trim());
        cacheUUID(key);
        persist();
        notify(mcMessageName(name), true);
    }

    public void removeFriend(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String key = name.toLowerCase().trim();
        if (!friends.remove(key)) {
            return;
        }
        friendDisplayNames.remove(key);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player.getName().getString().toLowerCase().equals(key)) {
                    friendUUIDs.remove(player.getUuid());
                    break;
                }
            }
        }
        persist();
        notify(mcMessageName(name), false);
    }

    public boolean isFriend(PlayerEntity player) {
        if (!isEnabled() || player == null) {
            return false;
        }
        if (friendUUIDs.contains(player.getUuid())) {
            return true;
        }
        String name = player.getName().getString().toLowerCase();
        if (friends.contains(name)) {
            friendUUIDs.add(player.getUuid());
            return true;
        }
        return false;
    }

    public boolean isFriend(String name) {
        return isEnabled() && name != null && friends.contains(name.toLowerCase().trim());
    }

    public Set<String> getFriends() {
        return friends;
    }

    private List<String> friendNamesForGui() {
        List<String> names = new ArrayList<>();
        for (String key : friends) {
            names.add(friendDisplayNames.getOrDefault(key, key));
        }
        return names;
    }

    public void toggleFriend(PlayerEntity player) {
        if (player == null) {
            return;
        }
        String name = player.getName().getString();
        if (isFriend(player)) {
            removeFriend(name);
        } else {
            addFriend(name);
        }
    }

    private void notify(String name, boolean added) {
        if (!chatNotify.get()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        // Chat messages encrypted with XOR key=53
        // "\u00a7aFriend \u00a7f" + name + " \u00a7aadded" / "\u00a7cFriend \u00a7f" + name + " \u00a7cremoved"
        String _prefix_add = _s(new int[]{146, 84, 115, 71, 92, 80, 91, 81, 21, 146, 83}, 53);
        String _prefix_rem = _s(new int[]{146, 86, 115, 71, 92, 80, 91, 81, 21, 146, 83}, 53);
        String _suffix_add = _s(new int[]{21, 146, 84, 84, 81, 81, 80, 81}, 53);
        String _suffix_rem = _s(new int[]{21, 146, 86, 71, 80, 88, 90, 67, 80, 81}, 53);
        String msg = added
                ? _prefix_add + name + _suffix_add
                : _prefix_rem + name + _suffix_rem;
        mc.player.sendMessage(Text.literal(msg), false);
    }

    private static String mcMessageName(String name) {
        return name.trim();
    }

    private void persist() {
        PersistenceHelper.saveFriends(friends);
    }

    private void cacheOnlineUuids() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            String name = player.getName().getString().toLowerCase();
            if (friends.contains(name)) {
                friendUUIDs.add(player.getUuid());
            }
        }
    }

    private void cacheUUID(String nameLower) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getName().getString().toLowerCase().equals(nameLower)) {
                friendUUIDs.add(player.getUuid());
                return;
            }
        }
    }

    @Override
    public void onEnable() {
        friends.clear();
        friendDisplayNames.clear();
        friends.addAll(PersistenceHelper.loadFriends());
        for (String key : friends) {
            friendDisplayNames.putIfAbsent(key, key);
        }
        friendUUIDs.clear();
        cacheOnlineUuids();
    }
}
