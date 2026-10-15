package me.animatednametags;

import org.bukkit.Color;

final class ColorUtil {

    private ColorUtil() {
    }

    /** Acepta RRGGBB (opaco) o AARRGGBB, con o sin '#'. Devuelve null si es invalido. */
    static Integer parseArgb(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.trim().replace("#", "");
        try {
            if (hex.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
            // invalido
        }
        return null;
    }

    static int lerp(int a, int b, double t) {
        int alpha = channel((a >>> 24) & 255, (b >>> 24) & 255, t);
        int red = channel((a >>> 16) & 255, (b >>> 16) & 255, t);
        int green = channel((a >>> 8) & 255, (b >>> 8) & 255, t);
        int blue = channel(a & 255, b & 255, t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    static Color toColor(int argb) {
        return Color.fromARGB((argb >>> 24) & 255, (argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255);
    }
}
