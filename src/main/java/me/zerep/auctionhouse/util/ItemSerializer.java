package me.zerep.auctionhouse.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.Base64;

public final class ItemSerializer {
    private ItemSerializer() {}

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeObject(item);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isBlank()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            return (ItemStack) in.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class BukkitObjectOutputStream extends ObjectOutputStream {
        BukkitObjectOutputStream(OutputStream out) throws IOException { super(out); }
        @Override protected void annotateClass(Class<?> cl) throws IOException { super.annotateClass(cl); }
    }

    private static class BukkitObjectInputStream extends ObjectInputStream {
        BukkitObjectInputStream(InputStream in) throws IOException { super(in); }
        @Override protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            return super.resolveClass(desc);
        }
    }
}
