import java.time.LocalDateTime;

public class Report {
    private int id;
    private int messageId;
    private int reporterId;
    private String reporterName;
    private String reason;
    private String status; // PENDING, APPROVED, REJECTED
    private String adminDecision;
    private LocalDateTime createdAt;
    private Message message;
    private User reportedUser;

    // Конструкторы
    public Report() {
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Report(int messageId, int reporterId, String reason) {
        this();
        this.messageId = messageId;
        this.reporterId = reporterId;
        this.reason = reason;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMessageId() { return messageId; }
    public void setMessageId(int messageId) { this.messageId = messageId; }

    public int getReporterId() { return reporterId; }
    public void setReporterId(int reporterId) { this.reporterId = reporterId; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminDecision() { return adminDecision; }
    public void setAdminDecision(String adminDecision) { this.adminDecision = adminDecision; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }

    public User getReportedUser() { return reportedUser; }
    public void setReportedUser(User reportedUser) { this.reportedUser = reportedUser; }

    // Бизнес-логика
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isResolved() {
        return "APPROVED".equals(status) || "REJECTED".equals(status);
    }

    public String getStatusDisplay() {
        switch (status) {
            case "PENDING": return "Ожидает рассмотрения";
            case "APPROVED": return "Одобрена";
            case "REJECTED": return "Отклонена";
            default: return status;
        }
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private int messageId;
        private int reporterId;
        private String reporterName;
        private String reason;
        private String status;
        private String adminDecision;
        private LocalDateTime createdAt;
        private Message message;
        private User reportedUser;

        public Builder id(int id) { this.id = id; return this; }
        public Builder messageId(int messageId) { this.messageId = messageId; return this; }
        public Builder reporterId(int reporterId) { this.reporterId = reporterId; return this; }
        public Builder reporterName(String reporterName) { this.reporterName = reporterName; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder adminDecision(String adminDecision) { this.adminDecision = adminDecision; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder message(Message message) { this.message = message; return this; }
        public Builder reportedUser(User reportedUser) { this.reportedUser = reportedUser; return this; }

        public Report build() {
            Report report = new Report();
            report.setId(id);
            report.setMessageId(messageId);
            report.setReporterId(reporterId);
            report.setReporterName(reporterName);
            report.setReason(reason);
            report.setStatus(status != null ? status : "PENDING");
            report.setAdminDecision(adminDecision);
            report.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());
            report.setMessage(message);
            report.setReportedUser(reportedUser);
            return report;
        }
    }
}