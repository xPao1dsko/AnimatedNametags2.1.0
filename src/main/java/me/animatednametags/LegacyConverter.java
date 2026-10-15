package me.animatednametags;

import java.util.Map;

/**
 * Convierte codigos legacy (&c, §c, &#rrggbb) a tags MiniMessage.
 */
final class LegacyConverter {

    private static final Map<Character, String> MAP = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>")
    );

    private LegacyConverter() {
    }

    static String convert(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                if (next == '#' && i + 8 <= input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append("<#").append(hex).append('>');
                        i += 7;
                        continue;
                    }
                }
                String mapped = MAP.get(next);
                if (mapped != null) {
                    out.append(mapped);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
