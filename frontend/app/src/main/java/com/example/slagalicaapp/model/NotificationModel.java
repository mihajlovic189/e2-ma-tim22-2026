package com.example.slagalicaapp.model;

public class NotificationModel {
    private String id;
    private String title;
    private String body;
    private long timestamp;
    private boolean isRead;
    private String type; // "chat", "ranking", "rewards", "other"

    public NotificationModel(String id, String title, String body, long timestamp, boolean isRead, String type) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.timestamp = timestamp;
        this.isRead = isRead;
        this.type = type != null ? type : "other";
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public String getType() { return type; }
}
