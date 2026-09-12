package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.ActionFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;
import net.minecraft.client.MinecraftClient;

import java.util.Random;


public class Config_NickHider extends ConfigCategoryImpl {

    
    private final StringFieldBuilder   fakeNick;
    private final StringFieldBuilder   prefix;
    private final StringFieldBuilder   suffix;
    private final BooleanToggleBuilder hideInChat;
    private final BooleanToggleBuilder hideInLeaderboard;
    private final BooleanToggleBuilder hideInScoreboard;
    private final BooleanToggleBuilder hideInNametag;
    private final BooleanToggleBuilder randomOnJoin;

    
    private final BooleanToggleBuilder fakeRankEnabled;
    private final EnumSelectorBuilder  fakeRankStyle;
    private final StringFieldBuilder   fakeRankText;
    private final EnumSelectorBuilder  fakeRankColor;

    
    
    private static final String STYLE_BRACKET  = "Bracket";   
    private static final String STYLE_BOLD     = "Bold";      
    private static final String STYLE_HASH     = "Hash";      
    private static final String STYLE_BARE     = "Bare";      
    private static final String STYLE_ITALIC   = "Italic";    

    
    private static final String[] COLOR_NAMES = {
        "Gold", "Aqua", "Green", "Red", "Yellow",
        "White", "Gray", "Blue", "Purple", "Pink"
    };
    private static final String[] COLOR_CODES = {
        "§6", "§b", "§a", "§c", "§e",
        "§f", "§7", "§9", "§5", "§d"
    };

    
    private static final String[] ADJ  = {
        "Fast","Dark","Swift","Silent","Ghost","Shadow","Neon","Cyber",
        "Hyper","Void","Arcane","Lunar","Storm","Iron","Blaze"
    };
    private static final String[] NOUN = {
        "Blade","Hunter","Runner","Sniper","Knight","Dragon","Phoenix",
        "Tiger","Wolf","Hawk","Viper","Raven","Specter","Reaper","Titan"
    };

    
    private String internalNick = null;

    public Config_NickHider() {
        super("NickHider", "Hide your real username", Cat.MISC);

        fakeNick          = new StringFieldBuilder("Fake Nick",            "", "Player1234", 24);
        prefix            = new StringFieldBuilder("Name Prefix",          "", "", 10);
        suffix            = new StringFieldBuilder("Name Suffix",          "", "", 10);
        hideInChat        = new BooleanToggleBuilder("Hide In Chat",        "", true);
        hideInLeaderboard = new BooleanToggleBuilder("Hide In Leaderboard", "", true);
        hideInScoreboard  = new BooleanToggleBuilder("Hide In Scoreboard",  "", true);
        hideInNametag     = new BooleanToggleBuilder("Hide In Nametag",     "", true);
        randomOnJoin      = new BooleanToggleBuilder("Random On Join",      "", false);

        fakeRankEnabled = new BooleanToggleBuilder("Fake Rank",            "", false);
        fakeRankStyle   = new EnumSelectorBuilder("Rank Style",            "", "Bracket",
            STYLE_BRACKET, STYLE_BOLD, STYLE_HASH, STYLE_BARE, STYLE_ITALIC);
        fakeRankText    = new StringFieldBuilder("Rank Text",              "", "VIP", 8);
        fakeRankColor   = new EnumSelectorBuilder("Rank Color",            "", "Gold",
            COLOR_NAMES);

        addSetting(new ActionFieldBuilder("Edit Nick", "Open nick editor", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.setScreen(new NickHiderScreen(this));
            }
        }));
        addSetting(hideInChat);
        addSetting(hideInLeaderboard);
        addSetting(hideInScoreboard);
        addSetting(hideInNametag);
        addSetting(randomOnJoin);
        addSetting(fakeRankEnabled);
        addSetting(fakeRankStyle);
        addSetting(fakeRankText);
        addSetting(fakeRankColor);
    }

    @Override
    public void onEnable() {
        if (randomOnJoin.get()) randomize();
    }

    

    
    public String applyNick(String input) {
        if (!isEnabled() || input == null) return input;
        String real = getRealName();
        if (real.isEmpty() || !input.contains(real)) return input;
        return input.replace(real, getDecoratedNick());
    }

    
    public String processDisplayName(String displayName) {
        if (!isEnabled() || displayName == null) return displayName;
        String real = getRealName();
        if (real.isEmpty()) return displayName;

        String result = displayName;

        
        if ((hideInScoreboard.get() || hideInLeaderboard.get()) && result.contains(real)) {
            result = result.replace(real, getDecoratedNick());
        }

        
        if (fakeRankEnabled.get() && result.contains(getActiveNick())) {
            String rank = buildRankString();
            if (!rank.isEmpty()) {
                result = result.replace(getActiveNick(), rank + getActiveNick());
            }
        }

        return result;
    }

    public String getDecoratedNick() {
        return prefix.get() + getActiveNick() + suffix.get();
    }

    public String getActiveNick() {
        return internalNick != null ? internalNick : fakeNick.get();
    }

    public String getRealName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "";
        return mc.player.getName().getString();
    }

    

    
    public String buildRankString() {
        if (!fakeRankEnabled.get()) return "";

        String style = fakeRankStyle.get();
        String text  = fakeRankText.get();
        String color = getColorCode();

        return switch (style) {
            case STYLE_BRACKET -> color + "[" + text + "]§r ";
            case STYLE_BOLD    -> color + "§l[" + text + "]§r ";
            case STYLE_HASH    -> color + "#" + text + " §r";
            case STYLE_BARE    -> color + text + " §r";
            case STYLE_ITALIC  -> color + "§o" + text + "§r ";
            default            -> color + "[" + text + "]§r ";
        };
    }

    private String getColorCode() {
        String selected = fakeRankColor.get();
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            if (COLOR_NAMES[i].equals(selected)) return COLOR_CODES[i];
        }
        return "§6"; 
    }

    

    public void randomize() {
        Random rng  = new Random();
        internalNick = ADJ[rng.nextInt(ADJ.length)]
                     + NOUN[rng.nextInt(NOUN.length)]
                     + (rng.nextInt(9000) + 1000);
    }

    
    public boolean isHideInChat()        { return hideInChat.get(); }
    public boolean isHideInLeaderboard() { return hideInLeaderboard.get(); }
    public boolean isHideInScoreboard()  { return hideInScoreboard.get(); }
    public boolean isHideInNametag()     { return hideInNametag.get(); }
    public boolean isFakeRankEnabled()   { return fakeRankEnabled.get(); }

    public String getFakeNickValue() { return fakeNick.get(); }
    public String getPrefixValue() { return prefix.get(); }
    public String getSuffixValue() { return suffix.get(); }
    public String getRankTextValue() { return fakeRankText.get(); }

    public void applyEditorValues(String nick, String pre, String suf, String rank) {
        fakeNick.set(nick == null ? "" : nick.trim());
        prefix.set(pre == null ? "" : pre);
        suffix.set(suf == null ? "" : suf);
        fakeRankText.set(rank == null ? "" : rank.trim());
    }
}
