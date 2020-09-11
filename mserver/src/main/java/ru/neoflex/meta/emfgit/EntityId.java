package ru.neoflex.meta.emfgit;

public class EntityId {
    private String id;
    private String rev;

    public EntityId() {

    }

    public EntityId(String id, String rev) {
        this.id = id;
        this.rev = rev;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRev() {
        return rev;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public String[] getIdPath() {
        return new String[] {id.substring(0, 2), id.substring(2)};
    }
}
