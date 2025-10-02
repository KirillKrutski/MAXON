class AdminManager {
    constructor() {
        this.currentAdmin = null;
        this.reports = [];
        this.users = [];
        this.init();
    }

    async init() {
        await this.checkAuth();
        this.setupEventListeners();
        this.loadReports();
        this.loadUsers();
        this.startPolling();
    }

    async checkAuth() {
        try {
            const response = await fetch('/api/user/current');
            if (response.ok) {
                this.currentAdmin = await response.json();
                if (this.currentAdmin.role !== 'ADMIN') {
                    window.location.href = '/chat';
                }
                document.getElementById('adminUsername').textContent = this.currentAdmin.username;
            } else {
                window.location.href = '/';
            }
        } catch (error) {
            console.error('Auth check failed:', error);
            window.location.href = '/';
        }
    }

    setupEventListeners() {
        // Logout
        document.getElementById('adminLogoutBtn').addEventListener('click', () => this.logout());

        // User search
        document.getElementById('userManagementSearch').addEventListener('input', (e) => this.searchUsers(e.target.value));

        // Decision modal
        document.getElementById('confirmDecision').addEventListener('click', () => this.confirmDecision());
        document.getElementById('cancelDecision').addEventListener('click', () => this.hideDecisionModal());

        // Close modal on outside click
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('modal')) {
                e.target.classList.add('hidden');
            }
        });
    }

    async loadReports() {
        try {
            const response = await fetch('/api/admin/reports');
            this.reports = await response.json();
            this.displayReports();
        } catch (error) {
            console.error('Load reports error:', error);
        }
    }

    displayReports() {
        const container = document.getElementById('reportsList');

        if (this.reports.length === 0) {
            container.innerHTML = '<div class="no-data">Нет жалоб для рассмотрения</div>';
            return;
        }

        container.innerHTML = this.reports.map(report => `
            <div class="report-item" data-report-id="${report.id}">
                <div class="report-header">
                    <strong>От: ${report.reporterName}</strong>
                    <span class="report-status ${report.isPending() ? 'pending' : 'resolved'}">
                        ${report.getStatusDisplay()}
                    </span>
                </div>
                <div class="report-reason">
                    <strong>Причина:</strong> ${report.reason}
                </div>
                ${report.message ? `
                    <div class="report-message">
                        <strong>Сообщение:</strong> "${report.message.content}"
                        <br><small>От: ${report.message.senderName}</small>
                    </div>
                ` : ''}
                ${report.isPending() ? `
                    <button class="btn-small resolve-report-btn">Рассмотреть</button>
                ` : ''}
                ${report.adminDecision ? `
                    <div class="admin-decision">
                        <strong>Решение администратора:</strong> ${report.adminDecision}
                    </div>
                ` : ''}
            </div>
        `).join('');

        // Add event listeners to resolve buttons
        document.querySelectorAll('.resolve-report-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const reportId = e.target.closest('.report-item').dataset.reportId;
                this.showDecisionModal(reportId);
            });
        });
    }

    async loadUsers() {
        try {
            const response = await fetch('/api/admin/users');
            this.users = await response.json();
            this.displayUsers();
        } catch (error) {
            console.error('Load users error:', error);
        }
    }

    displayUsers() {
        const container = document.getElementById('usersList');
        container.innerHTML = this.users.map(user => `
            <div class="user-item">
                <div class="user-header">
                    <strong>${user.username}</strong>
                    <span class="user-status ${user.isCurrentlyBlocked() ? 'blocked' : 'active'}">
                        ${user.isCurrentlyBlocked() ? 'Заблокирован' : 'Активен'}
                    </span>
                </div>
                <div class="user-actions">
                    ${!user.isCurrentlyBlocked() ? `
                        <button class="btn-small block-user-btn" data-user-id="${user.id}">Заблокировать</button>
                    ` : `
                        <button class="btn-small unblock-user-btn" data-user-id="${user.id}">Разблокировать</button>
                    `}
                </div>
            </div>
        `).join('');

        // Add event listeners to block/unblock buttons
        document.querySelectorAll('.block-user-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.dataset.userId;
                this.showBlockModal(userId);
            });
        });

        document.querySelectorAll('.unblock-user-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.dataset.userId;
                this.unblockUser(userId);
            });
        });
    }

    searchUsers(query) {
        const filteredUsers = this.users.filter(user =>
            user.username.toLowerCase().includes(query.toLowerCase())
        );

        const container = document.getElementById('usersList');
        // Re-render filtered users (simplified)
        if (query.trim()) {
            container.innerHTML = filteredUsers.map(user => `
                <div class="user-item">
                    <div class="user-header">
                        <strong>${user.username}</strong>
                        <span class="user-status ${user.isCurrentlyBlocked() ? 'blocked' : 'active'}">
                            ${user.isCurrentlyBlocked() ? 'Заблокирован' : 'Активен'}
                        </span>
                    </div>
                    <div class="user-actions">
                        ${!user.isCurrentlyBlocked() ? `
                            <button class="btn-small block-user-btn" data-user-id="${user.id}">Заблокировать</button>
                        ` : `
                            <button class="btn-small unblock-user-btn" data-user-id="${user.id}">Разблокировать</button>
                        `}
                    </div>
                </div>
            `).join('');
        } else {
            this.displayUsers(); // Show all users
        }
    }

    showDecisionModal(reportId) {
        this.currentReportId = reportId;
        const report = this.reports.find(r => r.id == reportId);

        if (!report) return;

        document.getElementById('reportDetails').innerHTML = `
            <p><strong>Жалоба от:</strong> ${report.reporterName}</p>
            <p><strong>Причина:</strong> ${report.reason}</p>
            <p><strong>Сообщение:</strong> "${report.message.content}"</p>
            <p><strong>Автор сообщения:</strong> ${report.message.senderName}</p>
        `;

        // Reset form
        document.querySelectorAll('input[name="decision"]').forEach(radio => {
            radio.checked = false;
        });
        document.getElementById('blockDays').value = '';

        document.getElementById('decisionModal').classList.remove('hidden');
    }

    hideDecisionModal() {
        document.getElementById('decisionModal').classList.add('hidden');
        this.currentReportId = null;
    }

    async confirmDecision() {
        const selectedDecision = document.querySelector('input[name="decision"]:checked');
        if (!selectedDecision) {
            alert('Выберите решение');
            return;
        }

        let days = 0;
        if (selectedDecision.value === 'block_temporary') {
            days = parseInt(document.getElementById('blockDays').value);
            if (!days || days < 1) {
                alert('Укажите количество дней блокировки');
                return;
            }
        }

        try {
            const response = await fetch(`/api/admin/reports/${this.currentReportId}/decide`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `decision=${selectedDecision.value}&days=${days}`
            });

            const data = await response.json();
            if (data.success) {
                this.hideDecisionModal();
                this.loadReports();
                this.loadUsers(); // Refresh users list in case of blocking
            } else {
                alert('Ошибка при обработке жалобы');
            }
        } catch (error) {
            console.error('Decision error:', error);
            alert('Ошибка при обработке жалобы');
        }
    }

    showBlockModal(userId) {
        this.blockingUserId = userId;
        // Similar to decision modal but for direct user blocking
        // Implementation would be similar to showDecisionModal
        alert('Функция прямой блокировки пользователя будет реализована аналогично');
    }

    async unblockUser(userId) {
        if (!confirm('Разблокировать пользователя?')) return;

        try {
            const response = await fetch(`/api/admin/users/${userId}/unblock`, {
                method: 'POST'
            });

            const data = await response.json();
            if (data.success) {
                this.loadUsers();
            } else {
                alert('Ошибка разблокировки пользователя');
            }
        } catch (error) {
            console.error('Unblock error:', error);
            alert('Ошибка разблокировки пользователя');
        }
    }

    startPolling() {
        setInterval(() => {
            this.loadReports();
            this.loadUsers();
        }, 5000); // Poll every 5 seconds
    }

    async logout() {
        try {
            await fetch('/api/logout', { method: 'POST' });
            window.location.href = '/';
        } catch (error) {
            console.error('Logout error:', error);
            window.location.href = '/';
        }
    }
}

// Initialize admin manager when page loads
document.addEventListener('DOMContentLoaded', () => {
    window.adminManager = new AdminManager();
});