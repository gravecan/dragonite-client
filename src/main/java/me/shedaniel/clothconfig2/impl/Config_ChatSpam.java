package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.ActionFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cycles through up to 10 custom chat messages. Click + to show another slot
 * (defaults to 3 visible). Optional lookalike-character bypass for soft filters.
 */
public class Config_ChatSpam extends ConfigCategoryImpl {

    public static Config_ChatSpam INSTANCE;

    private static final int MAX_MESSAGES = 10;
    private static final String[] DEFAULTS = {
            "Tired of losing in hvh fights? Join dragonite! .gg/dragoniteclient",
            "Wanna own other cheaters? Get ready and join dragonite client discord.gg/dragoniteclient",
            "Looking for the best client? Dragonite — discord.gg/dragoniteclient",
            "", "", "", "", "", "", ""
    };

    private final DoubleFieldBuilder delay;
    private final BooleanToggleBuilder bypassFilters;
    private final BooleanToggleBuilder randomOrder;
    private final ActionFieldBuilder addMessage;
    private final ActionFieldBuilder removeMessage;
    private final StringFieldBuilder[] messages = new StringFieldBuilder[MAX_MESSAGES];

    private int visibleCount = 3;
    private int nextIndex;
    private long nextSendMs;

    public Config_ChatSpam() {
        super("Chat Spam", "Spam rotating promo messages in chat", Cat.MISC);
        INSTANCE = this;
        setTooltip("Up to 10 messages. Click + to add another slot. Delay is seconds between sends.");

        delay = new DoubleFieldBuilder("Spam Delay", "Seconds between messages", 1.5, 0.1, 5.0, 0.1);
        delay.setSuffix("s");
        bypassFilters = new BooleanToggleBuilder(
                "Bypass Chat Filters",
                "Swap letters for lookalike characters so soft chat filters miss the text",
                false);
        randomOrder = new BooleanToggleBuilder("Random Order", "Pick a random filled message each send", true);

        addMessage = new ActionFieldBuilder("+ Add Message", "Show another message slot (max 10)", this::addSlot);
        removeMessage = new ActionFieldBuilder("- Remove Message", "Hide the last message slot (min 1)", this::removeSlot);

        addSetting(delay);
        addSetting(bypassFilters);
        addSetting(randomOrder);
        addSetting(addMessage);
        addSetting(removeMessage);

        for (int i = 0; i < MAX_MESSAGES; i++) {
            final int index = i;
            messages[i] = new StringFieldBuilder(
                    "Message " + (i + 1),
                    "Chat line " + (i + 1),
                    DEFAULTS[i],
                    256);
            messages[i].setVisibleWhen(() -> index < visibleCount);
            addSetting(messages[i]);
        }
    }

    private void addSlot() {
        if (visibleCount < MAX_MESSAGES) {
            visibleCount++;
        }
    }

    private void removeSlot() {
        if (visibleCount > 1) {
            visibleCount--;
        }
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSendMs) {
            return;
        }

        List<String> filled = filledMessages();
        if (filled.isEmpty()) {
            return;
        }

        String raw;
        if (randomOrder.get()) {
            raw = filled.get(ThreadLocalRandom.current().nextInt(filled.size()));
        } else {
            raw = filled.get(nextIndex % filled.size());
            nextIndex++;
        }

        String out = bypassFilters.get() ? HomoglyphBypass.apply(raw) : raw;
        if (out.isBlank()) {
            return;
        }
        if (out.length() > 256) {
            out = out.substring(0, 256);
        }

        mc.getNetworkHandler().sendChatMessage(out);
        nextSendMs = now + Math.round(delay.get() * 1000.0);
    }

    private List<String> filledMessages() {
        List<String> out = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            String value = messages[i].get();
            if (value != null && !value.isBlank()) {
                out.add(value.trim());
            }
        }
        return out;
    }

    @Override
    public void onEnable() {
        nextSendMs = System.currentTimeMillis() + Math.round(delay.get() * 1000.0);
        nextIndex = 0;
    }
}
