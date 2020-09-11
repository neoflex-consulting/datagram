package ru.neoflex.meta.emfgit;

import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitFileSystemProvider;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.filesystem.utils.GfsUriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.file.Files;

public class GitURLStreamHandler extends URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new URLConnection(u) {

            @Override
            public void connect() throws IOException {
            }

            @Override
            public InputStream getInputStream() throws IOException {
                GitFileSystemProvider provider = GitFileSystemProvider.getDefault();
                try {
                    URI uri = url.toURI();
                    GitFileSystem gfs = provider.getFileSystem(uri);
                    String file = GfsUriUtils.getFile(uri);
                    GitPath path = gfs.getPath(file);
                    return Files.newInputStream(path);
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }

            }
        };
    }

    public static void registerFactory() throws Exception  {
        final Field factoryField = URL.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        final Field lockField = URL.class.getDeclaredField("streamHandlerLock");
        lockField.setAccessible(true);

        // use same lock as in java.net.URL.setURLStreamHandlerFactory
        synchronized (lockField.get(null)) {
            final URLStreamHandlerFactory urlStreamHandlerFactory = (URLStreamHandlerFactory) factoryField.get(null);
            // Reset the value to prevent Error due to a factory already defined
            factoryField.set(null, null);
            URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
                @Override
                public URLStreamHandler createURLStreamHandler(String protocol) {
                    if (protocol.equals("gfs")) {
                        return new GitURLStreamHandler();
                    }
                    return null;
                }
            });
        }
    }
}
