package MetaServer.utils

import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EPackage
import ru.neoflex.meta.utils.Context

/**
 * Created by orlov on 22.02.2017.
 */
class MPackage {
    EPackage ePackage

    MPackage(EPackage ePackage) {
        this.ePackage = ePackage
    }

    static MPackage wrap(EPackage ePackage) {
        return new MPackage(ePackage)
    }

    static List<MPackage> all() {
        return Context.current.contextSvc.teneoSvc.hbds.getEPackages().collect {wrap(it)}
    }

    static MPackage get(String nsPrefix) {
        return all().find {it.ePackage.nsPrefix == nsPrefix}
    }

    List<MClass> classes() {
        ePackage.EClassifiers.findAll {it instanceof EClass}.collect {MClass.wrap(it)}
    }
}
