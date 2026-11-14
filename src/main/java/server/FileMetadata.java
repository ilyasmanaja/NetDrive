package server;

public class FileMetadata {
    public String fileName;
    public long fileSize;
    public String uploadedBy;
    public String uploadedAt;

    public FileMetadata(String fileName, long fileSize, String uploadedBy, String uploadedAt) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }
}