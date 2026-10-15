package me.animatednametags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Una linea del nametag: uno o varios "frames" que rotan cada {@code interval} ticks.
 */
public record LineSpec(List<String> frames, int interval) {

    public static LineSpec single(String text) {
        return new LineSpec(List.of(text), 10);
    }

    static LineSpec parse(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return single(s);
        }
        if (o instanceof Map<?, ?> map) {
            List<String> frames = new ArrayList<>();
            if (map.get("frames") instanceof List<?> list) {
                for (Object x : list) {
                    frames.add(String.valueOf(x));
                }
            }
            if (frames.isEmpty()) {
                return null;
            }
            int interval = map.get("interval") instanceof Number n ? Math.max(1, n.intValue()) : 10;
            return new LineSpec(List.copyOf(frames), interval);
        }
        return single(String.valueOf(o));
    }

    Object serialize() {
        if (frames.size() <= 1) {
            return frames.isEmpty() ? "" : frames.get(0);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("frames", new ArrayList<>(frames));
        map.put("interval", interval);
        return map;
    }
}
