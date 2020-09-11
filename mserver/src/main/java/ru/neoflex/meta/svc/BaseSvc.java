package ru.neoflex.meta.svc;

import java.io.File;

/**
 * Created by orlov on 22.06.2015.
 */
public class BaseSvc {
    public static File getMSpaceDir() {
        File mSpaceDir = new File(System.getProperty("mspace.dir", System.getProperty("user.dir") + "/mspace"));
        return mSpaceDir;
    }
    public static File getTempDir() {
        File mSpaceDir = new File(System.getProperty("temp.dir", System.getProperty("user.dir") + "/temp"));
        return mSpaceDir;
    }
    public static File getDeployDir() {
        String deployDir = System.getProperty("deploy.dir");
        if (deployDir != null) {
            return new File(deployDir);
        }
        return new File(getMSpaceDir(), "deployments/" + getCustomerCode());
    }

    public static File getLibDir(){
        String libDir = System.getProperty("lib.dir");
        if (libDir != null) {
            return new File(libDir);
        }
        return getMSpaceDir();
    }

    public static String getCustomerCode() {
        return System.getProperty("cust.code", "default");
    }
}
