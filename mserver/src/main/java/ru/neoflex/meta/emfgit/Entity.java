package ru.neoflex.meta.emfgit;

public class Entity extends EntityId {
    private byte[] content;
    public Entity(String id, String rev, byte[] content) {
        super(id, rev);
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
