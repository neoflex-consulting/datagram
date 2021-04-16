import org.eclipse.emf.emfatic.core.generator.ecore.EcoreGenerator;

import java.io.File;

public class Emfatic2Ecore {
    public static void main(String[] args) {
        for (String arg: args) {
            EcoreGenerator ecoreGenerator = new EcoreGenerator();
            try {
                ecoreGenerator.generate(new File(arg), true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
