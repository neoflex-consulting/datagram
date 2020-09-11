package org.eclipse.emf.teneo.hibernate.mapping;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.teneo.hibernate.HbMapperException;

/**
 * Created by orlov on 16.06.2015.
 * грязный хак, чтобы получить доступ к защищённому enumInstance
 */
public class DynamicENumUserTypeProxy {
    private DynamicENumUserType instance;

    public DynamicENumUserTypeProxy(DynamicENumUserType dynamicENumUserType) {
        instance = dynamicENumUserType;
    }

    public Enumerator getEnumerator(String name) {
        Enumerator enumValue = instance.enumInstance.getEEnumLiteralByLiteral(name);
        if (enumValue == null) {
            throw new HbMapperException("The enum value " + name + " is not valid for enumerator: "
                    + instance.enumInstance.getName());
        }
        return enumValue;

    }
}
