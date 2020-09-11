package ru.neoflex.meta.emfgit;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class GitInputStream extends InputStream implements URIConverter.Loadable {
    private GitHandler handler;
    private URI uri;
    private Map<?, ?> options;

    public GitInputStream(GitHandler handler, URI uri, Map<?, ?> options) {
        this.handler = handler;
        this.uri = uri;
        this.options = options;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public void loadResource(Resource resource) throws IOException {
        Transaction transaction = handler.getTransaction();
        Database db = transaction.getDatabase();
        String id = db.checkAndGetId(uri);
        EntityId entityId = new EntityId(id, null);
        Entity entity = transaction.load(entityId);
        String rev = entity.getRev();
        if (!resource.getContents().isEmpty()) {
            resource.getContents().clear();
        }
        ((XMIResourceImpl) resource).doLoad(new ByteArrayInputStream(entity.getContent()), options);
        URI newURI = db.createURI(id, rev);
        resource.setURI(newURI);
        db.getEvents().fireAfterLoad(resource, transaction);
    }
}
