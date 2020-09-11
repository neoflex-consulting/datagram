package ru.neoflex.meta.emfgit;

import org.eclipse.emf.ecore.resource.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Events {
    public interface AfterLoad {
        void handle(Resource resource, Transaction tx) throws IOException;
    }
    private List<AfterLoad> afterLoadList = new ArrayList<>();
    public void fireAfterLoad(Resource resource, Transaction tx) throws IOException {
        for (AfterLoad handler: afterLoadList) {
            handler.handle(resource, tx);
        }
    }
    public void registerAfterLoad(AfterLoad handler) {
        afterLoadList.add(handler);
    }

    public interface BeforeSave {
        void handle(Resource old, Resource resource, Transaction tx) throws IOException;
    }
    private List<BeforeSave> beforeSaveList = new ArrayList<>();
    public void fireBeforeSave(Resource old, Resource resource, Transaction tx) throws IOException {
        for (BeforeSave handler: beforeSaveList) {
            handler.handle(old, resource, tx);
        }
    }
    public void registerBeforeSave(BeforeSave handler) {
        beforeSaveList.add(handler);
    }

    public interface AfterSave {
        void handle(Resource old, Resource resource, Transaction tx) throws IOException;
    }
    private List<AfterSave> afterSaveList = new ArrayList<>();
    public void fireAfterSave(Resource old, Resource resource, Transaction tx) throws IOException {
        for (AfterSave handler: afterSaveList) {
            handler.handle(old, resource, tx);
        }
    }
    public void registerAfterSave(AfterSave handler) {
        afterSaveList.add(handler);
    }

    public interface BeforeDelete {
        void handle(Resource resource, Transaction tx) throws IOException;
    }
    private List<BeforeDelete> beforeDeleteList = new ArrayList<>();
    public void fireBeforeDelete(Resource resource, Transaction tx) throws IOException {
        for (BeforeDelete handler: beforeDeleteList) {
            handler.handle(resource, tx);
        }
    }
    public void registerBeforeDelete(BeforeDelete handler) {
        beforeDeleteList.add(handler);
    }
}
