import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.emfatic.core.generator.ecore.EcoreGenerator;
import org.eclipse.epsilon.emc.emf.EmfUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Emfatic2Ecore {
    private static boolean contains(Path path, String sub) {
        for (Path s1: path) {
            if (s1.toString().equals(sub)) return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        List<String> ecorePaths = new ArrayList<>();
        List<String> emfPaths = new ArrayList<>();
        for (String arg: args) {
            Files.find(Paths.get(arg),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().endsWith(".ecore") && !contains(filePath, "target"))
                    .forEach((path)->ecorePaths.add(path.toString()));
            Files.find(Paths.get(arg),
                    Integer.MAX_VALUE,
                    (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().endsWith(".emf") && !contains(filePath, "target"))
                    .forEach((path)->emfPaths.add(path.toString()));
        }
        System.err.println("--- To load: " + ecorePaths.size() + " ecore files");
        for (String ecorePath: ecorePaths) {
            EmfUtil.register(URI.createFileURI(ecorePath), EPackage.Registry.INSTANCE);
            System.err.println("Loaded: " + ecorePath);
        }
        int lastPass = Integer.MAX_VALUE;
        Exception lastErr = null;
        while (0 < emfPaths.size() && emfPaths.size() < lastPass) {
            System.err.println("--- To process: " + emfPaths.size() + " emf files");
            lastPass = emfPaths.size();
            List<String> currPaths = new ArrayList<>(emfPaths);
            emfPaths.clear();
            for (String currPath: currPaths) {
                EcoreGenerator ecoreGenerator = new EcoreGenerator();
                try {
                    File emfFile = new File(currPath);
                    ecoreGenerator.generate(emfFile, true);
                    System.err.println("Processed: " + currPath);
                    String ecorePath = emfFile.getAbsolutePath().replaceAll("\\.emf$", ".ecore");
                    EmfUtil.register(URI.createFileURI(ecorePath), EPackage.Registry.INSTANCE);
                    System.err.println("Loaded: " + ecorePath);
                } catch (Exception e) {
                    System.err.println("Failed: " + currPath);
                    emfPaths.add(currPath);
                    lastErr = e;
                }
            }
        }
        if (lastErr != null && emfPaths.size() > 0) {
            lastErr.printStackTrace();
        }
    }
}
