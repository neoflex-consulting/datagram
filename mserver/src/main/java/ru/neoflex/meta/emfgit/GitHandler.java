package ru.neoflex.meta.emfgit;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.URIHandlerImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class GitHandler extends URIHandlerImpl {
    private Transaction transaction;

    public GitHandler(Transaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public boolean canHandle(URI uri) {
        return Database.GITDB.equals(uri.scheme());
    }

    public void delete(URI uri, Map<?, ?> options) throws IOException {
        Database db = transaction.getDatabase();
        Resource resource = db.createResourceSet(transaction).getResource(uri, true);
        String id = db.getId(uri);
        String rev = db.getRev(uri);
        EntityId entityId = new EntityId(id, rev);
//        Entity old = transaction.load(entityId);
//        Resource resource = db.entityToResource(transaction, old);
        db.getEvents().fireBeforeDelete(resource, transaction);
        transaction.delete(entityId);
    }

    @Override
    public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
        return new GitInputStream(this, uri, options);
    }

    @Override
    public OutputStream createOutputStream(URI uri, Map<?, ?> options) throws IOException {
        return new GitOutputStream(this, uri, options);
    }


    public Transaction getTransaction() {
        return transaction;
    }
}
