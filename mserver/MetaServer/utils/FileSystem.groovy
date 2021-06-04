package MetaServer.utils

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.StandardCopyOption;

/**
 * Created by orlov on 24.01.2016.
 */
class FileSystem {
    static void forceDeleteFolder(Path path){
        if(Files.exists(path)){
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.delete(file);
                    }
                    catch (Throwable ex) {
                        println(ex)
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    }
                    catch (Throwable ex) {
                        println(ex)
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
        }

    }

    static  void copyFolder(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    copy(file, dest.resolve(src.relativize(file)));
                }
                catch (Throwable ex) {
                    ex.printStackTrace()
                    println(ex)
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                try {
                    def targetPath = dest.resolve(src.relativize(dir))
                    if(!Files.exists(targetPath)){
                        Files.createDirectories(targetPath);
                    }
                    //copy(dir, targetPath);
                }
                catch (Throwable ex) {
                    ex.printStackTrace()
                    println(ex)
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void copy(Path source, Path dest) {
        try {
            if(!Files.exists(dest)){
                try{
                    Files.createDirectories(dest);
                }catch(Exception e){
                    //e.printStackTrace()
                }
            }
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            println("Copy :" + source.toAbsolutePath() + ", to:" + dest.toAbsolutePath())
        } catch (Exception e) {
            e.printStackTrace()
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
