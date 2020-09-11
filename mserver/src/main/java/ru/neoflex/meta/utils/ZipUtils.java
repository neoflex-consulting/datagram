package ru.neoflex.meta.utils;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {
    public static void zipInputStream(String path, InputStream inputStream, ZipOutputStream zipOutputStream) throws IOException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        ZipEntry zipEntry = new ZipEntry(path);
        zipOutputStream.putNextEntry(zipEntry);
        if (inputStream != null) {
            IOUtils.copy(inputStream, zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    public static void zipDirectoryEntry(String path, ZipOutputStream zipOutputStream) throws IOException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        ZipEntry zipEntry = new ZipEntry(path + "/");
        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.closeEntry();
    }

    public static void zipFile(String basePath, File file, ZipOutputStream zipOutputStream) throws IOException {
        String path = basePath + "/" + file.getName();
        if (file.isDirectory()) {
            zipDirectoryEntry(path, zipOutputStream);
        }
        else if (file.isFile()) {
            InputStream is = new FileInputStream(file);
            try {
                zipInputStream(path, is, zipOutputStream);
            }
            finally {
                is.close();
            }
        }
    }

    public static int unzipToDirectory(File directory, InputStream inputStream) {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        int count = 0;
        try {
            try {
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                while (zipEntry != null) {
                    File file = new File(directory, zipEntry.getName());
                    if (zipEntry.isDirectory()) {
                        file.mkdir();
                    }
                    else {
                        byte[] buffer = new byte[4096];
                        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file), buffer.length);
                        try {
                            int length;
                            while ((length = zipInputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                        }
                        finally {
                            outputStream.close();
                        }
                    }
                    ++count;
                    zipEntry = zipInputStream.getNextEntry();
                }
            }
            finally {
                zipInputStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count;
    }
    public static void zipDirectory(File directory, OutputStream outputStream, boolean recursive, String excludes) {
        ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream);
        try {
            try {
                zipDirectory("", directory, zipOutputStream, recursive, excludes);
            }
            finally {
                zipOutputStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void zipDirectory(String basePath, File directory, ZipOutputStream zipOutputStream, boolean recursive, String excludes) throws IOException {
        for (File file: directory.listFiles()) {
            if (excludes == null || !file.getName().matches(excludes)) {
                if (file.isDirectory()) {
                    if (recursive) {
                        zipFile(basePath, file, zipOutputStream);
                        String dirPath = basePath + "/" + file.getName();
                        zipDirectory(dirPath, file, zipOutputStream, recursive, excludes);
                    }
                }
                else {
                    zipFile(basePath, file, zipOutputStream);
                }
            }
        }
    }
}
