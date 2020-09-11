package ru.neoflex.meta.utils;

import org.eclipse.emf.emfatic.core.generator.ecore.EcoreGenerator;

import java.io.File;

/**
 * Created by orlov on 11.06.2015.
 */
public class Emfatic {
    static public File emf2ecore(File emfFile) {
        try {
            EcoreGenerator ecoreGenerator = new EcoreGenerator();
            ecoreGenerator.generate(emfFile, true);
            File ecoreFile = new File(emfFile.getAbsolutePath().replaceAll("\\.emf$", ".ecore"));
            assert ecoreFile.isFile();
            return ecoreFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
