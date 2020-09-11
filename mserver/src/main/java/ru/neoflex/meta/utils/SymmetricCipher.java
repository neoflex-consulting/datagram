package ru.neoflex.meta.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;

public class SymmetricCipher {
    private final static Log logger = LogFactory.getLog(SymmetricCipher.class);
    public static byte[] getKey() {
        if ("true".compareToIgnoreCase(System.getProperty("encrypt.passwords", "false")) != 0) {
            return null;
        }
        try {
            String keyFileName = System.getProperty("user.dir") + "/skey.bin";
            File keyFile = new File(keyFileName);
            if (keyFile.isFile()) {
                byte[] key = new byte[16];
                InputStream stream = new FileInputStream(keyFile);
                try {
                    stream.read(key);
                }
                finally {
                    stream.close();
                }
                return key;
            }
            else {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(128);
                SecretKey secretKey = keyGenerator.generateKey();
                byte[] key = secretKey.getEncoded();
                OutputStream stream = new FileOutputStream(keyFile);
                try {
                    stream.write(key);
                }
                finally {
                    stream.close();
                }
                return key;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static String encrypt(String plain) {
        byte[] key = getKey();
        if (key == null || plain == null) {
            return plain;
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] output = cipher.doFinal(plain.getBytes("UTF-8"));
            String result = Base64.encodeBase64String(output);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static String decrypt(String encrypted) {
        byte[] key = getKey();
        if (key == null || encrypted == null) {
            return encrypted;
        }
        byte[] input = Base64.decodeBase64(encrypted);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] output = cipher.doFinal(input);
            String result = new String(output, "UTF-8");
            return result;
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }
}
