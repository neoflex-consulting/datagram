package ru.neoflex.meta.emfgit;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class TransactionClassLoader extends ClassLoader {

    public TransactionClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) {
            url = super.getResource(name);
        }
        return url;
    }

    @Override
    protected URL findResource(String name) {
        Transaction tx = Transaction.getCurrent();
        if (tx == null) {
            return null;
        }
        Path resolved = tx.getFileSystem().getRootPath().resolve(name);
        if (!Files.exists(resolved)) {
            return null;
        }
        try {
            return resolved.toUri().toURL();
        } catch (IOException e) {
            throw new RuntimeException("could not open " + resolved, e);
        }
    }

    static public void withClassLoader(Runnable f) {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = new TransactionClassLoader(parent);
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            f.run();
        } finally {
            Thread.currentThread().setContextClassLoader(parent);
        }
    }

    static public<R> R withClassLoader(Callable<R> f) throws Exception {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = new TransactionClassLoader(parent);
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            return f.call();
        } finally {
            Thread.currentThread().setContextClassLoader(parent);
        }
    }
}
