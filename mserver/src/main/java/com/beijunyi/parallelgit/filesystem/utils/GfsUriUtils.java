package com.beijunyi.parallelgit.filesystem.utils;

import java.net.URI;
import java.nio.file.ProviderMismatchException;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitFileSystemProvider;

public final class GfsUriUtils {

  public final static String SID_KEY = "sid";

  static void checkScheme(URI uri) throws ProviderMismatchException {
    if(!GitFileSystemProvider.GFS.equalsIgnoreCase(uri.getScheme()))
      throw new ProviderMismatchException(uri.getScheme());
  }

  @Nonnull
  public static String getRepository(URI uri) {
    checkScheme(uri);
    GitFileSystem fs = GitFileSystemProvider.getDefault().getFileSystem(getSession(uri));
    return fs.getObjectService().getRepository().getDirectory().getAbsolutePath();
  }

  @Nonnull
  public static String getFile(URI uri) throws ProviderMismatchException {
    checkScheme(uri);
    return "/" + uri.getPath().split("/", 3)[2];
  }

  @Nullable
  public static String getSession(URI uri) throws ProviderMismatchException {
    checkScheme(uri);
    return uri.getPath().split("/", 3)[1];
  }
}
