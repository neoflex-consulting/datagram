package org.hibernate.tuple.entity;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.binding.EntityBinding;
import org.hibernate.tuple.Instantiator;
import org.hibernate.tuple.entity.EntityMetamodel;
import ru.neoflex.meta.utils.ModifiedMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by orlov on 15.04.2015.
 */
public class CustomMapTuplizerImpl extends org.hibernate.tuple.entity.DynamicMapEntityTuplizer {
    CustomMapTuplizerImpl(EntityMetamodel entityMetamodel, PersistentClass mappedEntity) {
        super(entityMetamodel, mappedEntity);
    }

    CustomMapTuplizerImpl(EntityMetamodel entityMetamodel, EntityBinding mappedEntity) {
        super(entityMetamodel, mappedEntity);
    }

    protected final Instantiator buildInstantiator(org.hibernate.mapping.PersistentClass mappingInfo) {
        return new CustomMapInstantiator( mappingInfo );
    }

    private static final class CustomMapInstantiator extends org.hibernate.tuple.DynamicMapInstantiator {
        public CustomMapInstantiator(PersistentClass mappingInfo) {
            super(mappingInfo);
        }

        // override the generateMap() method to return our custom map...
        protected final Map generateMap() {
            return new ModifiedMap();
        }
    }}
