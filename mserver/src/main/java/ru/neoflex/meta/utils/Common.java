package ru.neoflex.meta.utils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by orlov on 03.08.2016.
 */
public class Common {
    public static String getPassword(String key) {
        String pwdPropsName = java.lang.System.getProperty("passwords", java.lang.System.getProperty("user.dir") + "/passwords.properties");
        try {
            InputStream is = new BufferedInputStream(new FileInputStream(pwdPropsName));
            try {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty(key);
            }
            finally {
                is.close();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static String getDecryptedPassword(String key, String password) {
        if (password != null && password.length() > 0) {
            return password;
        }
        return SymmetricCipher.decrypt(getPassword(key));
    }
}
