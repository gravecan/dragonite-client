package me.shedaniel.clothconfig2.impl;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Soft chat-filter evasion via lookalike Unicode characters.
 * Keeps promo text readable while changing the exact code points.
 */
public final class HomoglyphBypass {

    private HomoglyphBypass() {}

    public static String apply(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            out.appendCodePoint(map(cp, rng));
        }
        return out.toString();
    }

    private static int map(int cp, ThreadLocalRandom rng) {
        // Only remap some letters so the message still looks normal.
        if (rng.nextFloat() > 0.55f) {
            return cp;
        }
        return switch (cp) {
            case 'a', 'A' -> pick(rng, 'а', 'ɑ', cp);      // Cyrillic a / Latin alpha
            case 'e', 'E' -> pick(rng, 'е', 'ɛ', cp);      // Cyrillic e
            case 'o', 'O' -> pick(rng, 'о', 'ο', cp);      // Cyrillic / Greek o
            case 'p', 'P' -> pick(rng, 'р', cp);           // Cyrillic er
            case 'c', 'C' -> pick(rng, 'с', 'ϲ', cp);      // Cyrillic es
            case 'x', 'X' -> pick(rng, 'х', 'ⅹ', cp);      // Cyrillic ha
            case 'y', 'Y' -> pick(rng, 'у', 'ɣ', cp);      // Cyrillic u
            case 'i', 'I' -> pick(rng, 'і', 'ι', cp);      // Ukrainian i / Greek iota
            case 'j', 'J' -> pick(rng, 'ј', cp);           // Cyrillic je
            case 's', 'S' -> pick(rng, 'ѕ', cp);           // Cyrillic dze
            case 'h', 'H' -> pick(rng, 'һ', 'ℎ', cp);
            case 'B' -> pick(rng, 'В', cp);
            case 'M' -> pick(rng, 'М', cp);
            case 'T' -> pick(rng, 'Τ', cp);
            case 'K' -> pick(rng, 'Κ', 'К', cp);
            case '.' -> pick(rng, '․', '.', cp);           // one-dot leader
            default -> cp;
        };
    }

    private static int pick(ThreadLocalRandom rng, int... options) {
        return options[rng.nextInt(options.length)];
    }
}
