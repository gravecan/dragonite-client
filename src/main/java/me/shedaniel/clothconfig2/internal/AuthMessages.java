package me.shedaniel.clothconfig2.internal;


public final class AuthMessages {

    public static final int MAX_LICENSE_ATTEMPTS = 5;

    private AuthMessages() {}

    public static String getAppealDiscord() {
        return "https://discord.gg/dragoniteclient";
    }

    public static String getBlacklistedMessage() {
        return "You have been blacklisted.\n\n"
                + "If you believe this was a false blacklist, create an appeal on our Discord:\n"
                + getAppealDiscord();
    }
}
