package ru.neoflex.meta.utils.teneo;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.teneo.hibernate.HbDataStore;
import org.eclipse.emf.teneo.hibernate.HbHelper;
import org.eclipse.emf.teneo.hibernate.HbMapperException;
import org.eclipse.emf.teneo.hibernate.tuplizer.EMFTuplizer;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;

import java.io.Serializable;

/**
 * Created by orlov on 22.07.2015.
 */
public class MEMFTuplizer extends EMFTuplizer {
    /**
     * Constructor
     *
     * @param entityMetamodel
     * @param mappedEntity
     */
    public MEMFTuplizer(EntityMetamodel entityMetamodel, PersistentClass mappedEntity) {
        super(entityMetamodel, mappedEntity);
    }

    @Override
    protected Instantiator buildInstantiator(PersistentClass persistentClass) {
        final HbDataStore ds = HbHelper.INSTANCE.getDataStore(persistentClass);
        final EClass eclass = ds.toEClass(persistentClass.getEntityName());
        if (eclass == null) {
            throw new HbMapperException("No eclass found for entityname: "
                    + persistentClass.getEntityName());
        }
        return new MEMFInstantiator(eclass, persistentClass);
    }

    public void setIdentifier(Object entity, Serializable id, SessionImplementor session) {
        super.setIdentifier(entity, id, session);
        if (entity instanceof MSerializableDynamicEObjectImpl) {
            ((MSerializableDynamicEObjectImpl) entity).setId(id);
        }
    }

    public void setPropertyValue(Object entity, int i, Object value) throws HibernateException {
        super.setPropertyValue(entity, i, value);
        if (getEntityMetamodel().getVersionPropertyIndex() == i) {
            if (entity instanceof MSerializableDynamicEObjectImpl) {
                ((MSerializableDynamicEObjectImpl) entity).setVersion(value);
            }
        }
    }

    public String determineConcreteSubclassEntityName(Object entityInstance, SessionFactoryImplementor factory) {

        if (entityInstance != null) {
            return super.determineConcreteSubclassEntityName(entityInstance, factory);
        }
        return this.getEntityName();
    }
}
