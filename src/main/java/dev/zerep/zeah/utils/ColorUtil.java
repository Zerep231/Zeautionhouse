package dev.zerep.zeah.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class ColorUtil {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {}

    public static Component color(String text) {
        return SERIALIZER.deserialize(text)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> colorList(List<String> lines) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) result.add(color(line));
        return result;
    }

    public static String strip(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "").replaceAll("\u00a7[0-9a-fk-or]", "");
    }

    public static String formatMaterial(String materialName) {
        String[] parts = materialName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String formatPrice(double price) {
        if (price >= 1_000_000) return String.format("%.1fM", price / 1_000_000);
        if (price >= 1_000) return String.format("%.1fK", price / 1_000);
        return String.format("%,.2f", price);
    }
}
