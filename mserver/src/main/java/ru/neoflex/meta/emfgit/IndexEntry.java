package ru.neoflex.meta.emfgit;

public class IndexEntry {
    private String[] path;
    private byte[] content;

    public String[] getPath() {
        return path;
    }

    public void setPath(String[] path) {
        this.path = path;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
