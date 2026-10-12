package ee.ahjusepad.store;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Väike JSON-i koostaja, et rakendus ei vajaks väliseid teeke.
 * Toetab API-s kasutatavaid väärtusi: teksti, arve, tõeväärtusi,
 * kaarte ja kogumeid.
 */
public final class Json {
    private Json() {}

    /**
     * Teisendab Java teksti korrektseks JSON-i tekstiväärtuseks.
     * Erimärgid, jutumärgid ja juhtmärgid varjestatakse JSON-i nõuete järgi.
     */
    public static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Koostab JSON-i JDK lihtväärtustest, kaartidest ja kogumitest.
     * Tundmatud objektid esitatakse tekstina, kasutades nende toString-väärtust.
     */
    public static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(value(entry.getValue()));
                if (iterator.hasNext()) out.append(',');
            }
            return out.append('}').toString();
        }
        if (value instanceof Collection<?> items) {
            StringBuilder out = new StringBuilder("[");
            Iterator<?> iterator = items.iterator();
            while (iterator.hasNext()) {
                out.append(value(iterator.next()));
                if (iterator.hasNext()) out.append(',');
            }
            return out.append(']').toString();
        }
        return quote(value.toString());
    }
}
