package ru.neoflex.meta.utils.vcs;

public class VCSInfo {
    private String lastCommitAuthor;
    private String lastChangedDate;
    private String lastChangedRevision;
    private String logMessage;

    public String getLastCommitAuthor() {
        return lastCommitAuthor;
    }

    public void setLastCommitAuthor(String lastCommitAuthor) {
        this.lastCommitAuthor = lastCommitAuthor;
    }

    public String getLastChangedDate() {
        return lastChangedDate;
    }

    public void setLastChangedDate(String lastChangedDate) {
        this.lastChangedDate = lastChangedDate;
    }

    public String getLastChangedRevision() {
        return lastChangedRevision;
    }

    public void setLastChangedRevision(String lastChangedRevision) {
        this.lastChangedRevision = lastChangedRevision;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }
}
