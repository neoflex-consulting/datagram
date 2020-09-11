package com.beijunyi.parallelgit.filesystem.utils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitFileSystemProvider;
import com.beijunyi.parallelgit.filesystem.GitPath;
import org.eclipse.jgit.lib.Repository;

public class GfsUriBuilder {

  private String sid;
  private String file;

  @Nonnull
  public static GfsUriBuilder prepare() {
    return new GfsUriBuilder();
  }

  @Nonnull
  public static GfsUriBuilder fromFileSystem(GitFileSystem gfs) {
    return prepare()
             .sid(gfs.getSessionId());
  }

  @Nonnull
  public GfsUriBuilder sid(@Nullable String session) {
    sid = session;
    return this;
  }

  @Nonnull
  public GfsUriBuilder file(@Nullable String filePathStr) {
    if (!filePathStr.startsWith("/")) {
      filePathStr = "/" + filePathStr;
    }
    this.file = filePathStr;
    return this;
  }

  @Nonnull
  public GfsUriBuilder file(@Nullable GitPath filePath) {
    return file(filePath != null ? filePath.toRealPath().toString() : null);
  }

  @Nonnull
  private String buildPath() {
    return "/" + sid + file;
  }

  @Nonnull
  public URI build() {
    try {
      return new URI(GitFileSystemProvider.GFS, null, buildPath(), null, null);
    } catch(URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
