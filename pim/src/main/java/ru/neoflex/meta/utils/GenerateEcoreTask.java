package ru.neoflex.meta.utils;


import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.emfatic.core.generator.ecore.EcoreGenerator;
import org.eclipse.epsilon.emc.emf.EmfUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GenerateEcoreTask extends Task {




    private File processFile(Path f) throws RuntimeException {
        try {
            EcoreGenerator ecoreGenerator = new EcoreGenerator();
            ecoreGenerator.generate(f.toFile(), true);
            File ecoreFile = new File(f.toFile().getAbsolutePath().replaceAll("\\.emf$", ".ecore"));
            log(String.format("Generating ecore %s", ecoreFile.getAbsolutePath()));
            assert ecoreFile.isFile();
            return ecoreFile;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static List<EPackage> registerPackages(File file) {
        return registerPackages(URI.createFileURI(file.getAbsolutePath()));
    }

    public static List<EPackage> registerPackages(URI resource) {
        try {
            return EmfUtil.register(resource, EPackage.Registry.INSTANCE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }





    @Override
    public void execute() throws BuildException {
        Project project = this.getProject();
        File baseDir = project.getBaseDir();
        log(String.format("GenerateEcoreTask, BaseDir %s", baseDir.getAbsolutePath()));
        try {

            Files.find(Paths.get(baseDir.toURI()),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> filePath.toFile().getName().endsWith(".ecore") && !filePath.toFile().getAbsolutePath().contains("target"))
                    .forEach(f -> registerPackages(f.toFile()));
            Files.find(Paths.get(baseDir.toURI()),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> filePath.toFile().getName().endsWith(".emf") && !filePath.toFile().getAbsolutePath().contains("target"))
                    .forEach(f -> processFile(f));


        }
        catch (IOException e) {
            e.printStackTrace();
            log("Error generating ecore", e, 1);
        }
    }


}
