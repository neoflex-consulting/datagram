package ru.neoflex.meta.utils.vcs;

import java.io.File;

public interface IVCSFactory {
    void init();
    IVCS create(File local, String userName, String password, String remote);
}
