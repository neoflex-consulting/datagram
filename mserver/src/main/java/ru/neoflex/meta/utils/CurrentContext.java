package ru.neoflex.meta.utils;

import ru.neoflex.meta.svc.ContextSvc;

/**
 * Created by orlov on 14.06.2015.
 */
public class CurrentContext {
    public Context get() { return ContextSvc.getCurrent(); }
}
