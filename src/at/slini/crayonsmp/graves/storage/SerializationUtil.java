package at.slini.crayonsmp.graves.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class SerializationUtil {
    public static byte[] toBytes(Object obj) throws Exception {
        if (obj == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos);
            try {
                oos.writeObject(obj);
                oos.flush();
                byte[] arrayOfByte = baos.toByteArray();
                oos.close();
                baos.close();
                return arrayOfByte;
            } catch (Throwable throwable) {
                try {
                    oos.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (Throwable throwable) {
            try {
                baos.close();
            } catch (Throwable throwable1) {
                throwable.addSuppressed(throwable1);
            }
            throw throwable;
        }
    }

    public static <T> T fromBytes(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return null;
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try {
            BukkitObjectInputStream ois = new BukkitObjectInputStream(bais);
            try {
                Object object = ois.readObject();
                ois.close();
                bais.close();
                return (T) object;
            } catch (Throwable throwable) {
                try {
                    ois.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (Throwable throwable) {
            try {
                bais.close();
            } catch (Throwable throwable1) {
                throwable.addSuppressed(throwable1);
            }
            throw throwable;
        }
    }
}
