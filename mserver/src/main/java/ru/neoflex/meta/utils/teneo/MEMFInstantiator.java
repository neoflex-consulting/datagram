package ru.neoflex.meta.utils.teneo;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.impl.DynamicEObjectImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.teneo.hibernate.HbMapperException;
import org.eclipse.emf.teneo.hibernate.mapping.SerializableDynamicEObjectImpl;
import org.eclipse.emf.teneo.hibernate.tuplizer.EMFInstantiator;
import org.eclipse.emf.teneo.type.PersistentStoreAdapter;
import org.eclipse.emf.teneo.util.StoreUtil;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.mapping.PersistentClass;

import java.io.Serializable;

/**
 * Created by orlov on 22.07.2015.
 */
public class MEMFInstantiator extends EMFInstantiator {
    private EClass eclass;

    public MEMFInstantiator(EClass eclass, PersistentClass pc) {
        super(eclass, pc);
        this.eclass = eclass;
    }

    public Object instantiate() {
        EObject eobject = new MSerializableDynamicEObjectImpl(eclass);

        final PersistentStoreAdapter adapter = StoreUtil.getPersistentStoreAdapter(eobject);
        adapter.setTargetCreatedByORM(true);

        if (eobject == null) {
            throw new HbMapperException("The mapped " + eclass.getInstanceClass().getName()
                    + " class can not be instantiated."
                    + " Possibly the class it is not an eclass or it is abstract.");
        }
        return eobject;
    }
}
