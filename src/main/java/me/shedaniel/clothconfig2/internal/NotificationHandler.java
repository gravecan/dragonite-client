package me.shedaniel.clothconfig2.internal;


public class NotificationHandler {

    public static void notifyIfEnabled(String username, String uuid, String hwid, String licenseType) {
        
    }

    public static void notify(String username, String uuid, String hwid, String licenseType) {
        notifyIfEnabled(username, uuid, hwid, licenseType);
    }
}
