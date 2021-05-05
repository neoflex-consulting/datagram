import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.emfatic.core.generator.ecore.EcoreGenerator;
import org.eclipse.epsilon.emc.emf.EmfUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Emfatic2Ecore {
    public static void main(String[] args) throws IOException {
        List<String> filePaths = new ArrayList<>();
        for (String arg: args) {
            if (new File(arg).isFile()) {
                filePaths.add(arg);
            }
            else if (new File(arg).isDirectory()) {
                Files.find(Paths.get(arg),
                        Integer.MAX_VALUE,
                        (filePath, fileAttr) -> fileAttr.isRegularFile())
                        .forEach((path)->filePaths.add(path.toString()));
            }
            else {
                System.err.println("Skipped: " + arg);
            }
        }
        int lastPass = Integer.MAX_VALUE;
        Exception lastErr = null;
        while (0 < filePaths.size() && filePaths.size() < lastPass) {
            System.err.println("--- Processing " + filePaths.size() + " files");
            lastPass = filePaths.size();
            List<String> currPaths = new ArrayList<>(filePaths);
            filePaths.clear();
            for (String currPath: currPaths) {
                EcoreGenerator ecoreGenerator = new EcoreGenerator();
                try {
                    if (currPath.endsWith(".emf")) {
                        File emfFile = new File(currPath);
                        ecoreGenerator.generate(emfFile, true);
                        System.err.println("Processed: " + currPath);
                        currPath = emfFile.getAbsolutePath().replaceAll("\\.emf$", ".ecore");
                    }
                    if (currPath.endsWith(".ecore")) {
                        EmfUtil.register(URI.createFileURI(currPath), EPackage.Registry.INSTANCE);
                        System.err.println("Loaded: " + currPath);
                    }
                } catch (Exception e) {
                    System.err.println("Failed: " + currPath);
                    filePaths.add(currPath);
                    lastErr = e;
                }
            }
        }
        if (lastErr != null && filePaths.size() > 0) {
            lastErr.printStackTrace();
        }
    }
}
