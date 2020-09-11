package ru.neoflex.meta.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by orlov on 20.05.2015.
 */
public class ModifiedMap extends HashMap {
    private int myhash = 37;

    @Override
    public Object put(Object o, Object o2) {
        if (o != null) {
            myhash ^= o.hashCode();
        }
        if (!(o2 == null) && !(o2 instanceof Collection)) {
            myhash ^= o2.hashCode();
        }
        return super.put(o, o2);
    }

    @Override
    public int hashCode() {
        return myhash;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        Map<?,?> m = (Map<?,?>) o;
        if (m.size() != size())
            return false;

        try {
            Iterator<Map.Entry> i = entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = i.next();
                Object key = e.getKey();
                Object value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!(value instanceof Collection)) {
                        if (!value.equals(m.get(key)))
                            return false;
                    }
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

}
