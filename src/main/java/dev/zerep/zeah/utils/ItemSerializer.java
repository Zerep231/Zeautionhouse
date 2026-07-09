package dev.zerep.zeah.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemSerializer {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<ItemStack, byte[]> CACHE = new LinkedHashMap<ItemStack, byte[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ItemStack, byte[]> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private ItemSerializer() {}

    public static byte[] serialize(ItemStack item) {
        synchronized(CACHE) {
            byte[] cached = CACHE.get(item);
            if (cached != null) return cached;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            byte[] data = baos.toByteArray();
            synchronized(CACHE) {
                CACHE.put(item.clone(), data);
            }
            return data;
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize item", e);
        }
    }

    public static ItemStack deserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize item", e);
        }
    }
}
