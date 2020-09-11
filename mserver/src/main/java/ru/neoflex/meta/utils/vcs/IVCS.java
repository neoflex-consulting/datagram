package ru.neoflex.meta.utils.vcs;

import java.io.File;
import java.util.List;

public interface IVCS {
    void checkout();
    void commit(String message);
    void commit(List<File> files, String message);
    void update();
    boolean isVersioned();
    VCSInfo getFileInfo(File file);
    void cleanup();
}
