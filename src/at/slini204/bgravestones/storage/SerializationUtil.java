package at.slini204.bgravestones.storage;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class SerializationUtil {

    private SerializationUtil() {
    }

    public static byte[] toBytes(Object obj) throws Exception {
        if (obj == null) return null;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromBytes(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return null;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
}
