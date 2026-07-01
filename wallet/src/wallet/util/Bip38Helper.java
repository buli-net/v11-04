package wallet.util;

import org.bitcoinj.crypto.ECKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;

public class Bip38Helper {
    public static String encrypt(ECKey key, String passphrase, boolean isMainNet) throws Exception {
        Class<?> bip38Class = Class.forName("org.bitcoinj.crypto.BIP38PrivateKey");

        byte[] privBytes = key.getPrivKeyBytes();
        BigInteger privBig = key.getPrivKey();
        boolean compressed = key.isCompressed();

        // Thử các signature thường gặp trong các bản bitcoinj
        Object[][] tries = {
            // bitcoinj 0.15+: encrypt(boolean, byte[], boolean, String)
            new Object[]{"encrypt", new Class[]{boolean.class, byte[].class, boolean.class, String.class},
                         new Object[]{isMainNet, privBytes, compressed, passphrase}},
            // bản cũ: encrypt(boolean, BigInteger, boolean, String)
            new Object[]{"encrypt", new Class[]{boolean.class, BigInteger.class, boolean.class, String.class},
                         new Object[]{isMainNet, privBig, compressed, passphrase}},
            // encrypt(String, ECKey)
            new Object[]{"encrypt", new Class[]{String.class, ECKey.class},
                         new Object[]{passphrase, key}},
            // encrypt(ECKey, String, boolean)
            new Object[]{"encrypt", new Class[]{ECKey.class, String.class, boolean.class},
                         new Object[]{key, passphrase, isMainNet}},
        };

        for (Object[] t : tries) {
            String name = (String) t[0];
            Class<?>[] params = (Class<?>[]) t[1];
            Object[] args = (Object[]) t[2];
            try {
                Method m = bip38Class.getMethod(name, params);
                Object result = m.invoke(null, args);
                return result.toString();
            } catch (NoSuchMethodException ignored) {}
        }

        // Thử constructor: new BIP38PrivateKey(ECKey, String, boolean)
        try {
            Constructor<?> c = bip38Class.getConstructor(ECKey.class, String.class, boolean.class);
            Object result = c.newInstance(key, passphrase, isMainNet);
            return result.toString();
        } catch (NoSuchMethodException ignored) {}

        throw new RuntimeException(
            "BIP38PrivateKey.encrypt không khớp với bản bitcoinj của bạn. " +
            "Nếu cần, thêm: implementation 'io.github.novacrypto:BIP38:0.9.5'"
        );
    }
}
