import java.time.LocalDateTime;

public class FriendRequest {
    private int id;
    private int fromUserId;
    private int toUserId;
    private String fromUsername;
    private String status; // PENDING, ACCEPTED, REJECTED
    private LocalDateTime createdAt;

    // Конструкторы
    public FriendRequest() {}

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFromUserId() { return fromUserId; }
    public void setFromUserId(int fromUserId) { this.fromUserId = fromUserId; }

    public int getToUserId() { return toUserId; }
    public void setToUserId(int toUserId) { this.toUserId = toUserId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}