package ru.neoflex.meta.utils.teneo;

import org.eclipse.emf.teneo.hibernate.HbContext;
import org.hibernate.cfg.Configuration;

/**
 * Created by orlov on 22.07.2015.
 */
public class MHbContext extends HbContext {
    public Class<?> getEMFTuplizerClass(Configuration hbConfiguration) {
        return MEMFTuplizer.class;
    }
}
