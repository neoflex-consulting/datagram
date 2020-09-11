package ru.neoflex.meta.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by orlov on 09.08.2016.
 */
public class FileSystem {
    public static void clearFolder(File folder) {
        forceDeleteFolder(folder.toPath(), true, "^[._].*");
    }
    public static void forceDeleteFolder(Path path) {
        forceDeleteFolder(path, false);
    }
    public static void forceDeleteFolder(final Path path, final boolean excludeRoot) {
        forceDeleteFolder(path, excludeRoot, null);
    }
    public static void forceDeleteFolder(final Path path, final boolean excludeRoot, final String excludes){
        if(Files.exists(path)){
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            if (excludes == null || !file.toFile().getName().matches(excludes)) {
                                file.toFile().setWritable(true);
                                Files.delete(file);
                            }
                        }
                        catch (Throwable ex) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        try {
                            if ((excludes == null || !dir.toFile().getName().matches(excludes)) && (!excludeRoot || !dir.equals(path)))
                            {
                                Files.delete(dir);
                            }
                        }
                        catch (Throwable ex) {
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (excludes == null || !dir.toFile().getName().matches(excludes))
                        {
                            return FileVisitResult.CONTINUE;
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                });

            }
            catch (Throwable th) {
            }
        }
    }
}
