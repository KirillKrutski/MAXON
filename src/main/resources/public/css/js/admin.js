// admin.js - ПОЛНАЯ РАБОЧАЯ ВЕРСИЯ БЕЗ ЗАГЛУШЕК
console.log("⚡ admin.js ЗАГРУЖЕН!");

const AdminManager = {
    users: [],
    currentBlockingUser: null,

    // 1. Инициализация
    async init() {
        console.log("🔄 Инициализация админ-панели...");
        const isAdmin = await checkAdminAuth();
        if (!isAdmin) return;

        await this.renderUsers();
        this.setupEventListeners();
        return this;
    },

    // 2. Настройка обработчиков событий
    setupEventListeners() {
        const refreshBtn = document.getElementById('refreshUsersBtn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.renderUsers());
        }
    },

    // 3. Загрузка данных пользователей с сервера
    async loadUsersData() {
        try {
            console.log("📥 Загрузка данных пользователей...");

            const response = await fetch('/api/admin/users', {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.checkAdminAuth()}`
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.success && data.users) {
                this.users = data.users;
                console.log("✅ Данные загружены:", this.users.length, "пользователей");
            } else {
                console.error('❌ Ошибка в данных:', data.message);
                this.users = [];
            }

            return this.users;

        } catch (error) {
            console.error('❌ Ошибка загрузки данных:', error);
            this.showError('Не удалось загрузить пользователей');
            return [];
        }
    },

    // 4. Получение токена авторизации
    async checkAdminAuth() {
        try {
            const response = await fetch('/api/user/current');
            if (!response.ok) {
                throw new Error('Not authenticated');
            }

            const user = await response.json();
            if (user.role !== 'ADMIN') {
                throw new Error('Not admin');
            }

            return true;
        } catch (error) {
            console.error('❌ Ошибка авторизации:', error);
            window.location.href = '/';
            return false;
        }
    },

    // 5. Отображение пользователей
    async renderUsers() {
        await this.loadUsersData();
        this.displayUsers();
        this.updateUserCount();
    },

    // 6. Обновление счетчика пользователей
    updateUserCount() {
        const counter = document.getElementById('userCount');
        if (counter) {
            counter.textContent = this.users.length;
        }
    },

    // 7. Метод отображения пользователей в интерфейсе
    displayUsers() {
        const container = document.getElementById('usersList');
        if (!container) {
            console.error("❌ Не найден элемент #usersList!");
            return;
        }

        if (this.users.length === 0) {
            container.innerHTML = '<div class="no-data">Пользователей нет</div>';
            return;
        }

        console.log("🎨 Отображаем", this.users.length, "пользователей...");

        container.innerHTML = this.users.map(user => {
            const isCurrentlyBlocked = this.isUserCurrentlyBlocked(user);
            const blockedText = user.blockedUntil ?
                ` до ${new Date(user.blockedUntil).toLocaleString('ru-RU')}` :
                '';

            return `
            <div class="user-item" data-user-id="${user.id}">
                <div class="user-header">
                    <strong>${user.username}</strong>
                    <span class="user-status ${isCurrentlyBlocked ? 'blocked' : 'active'}">
                        ${isCurrentlyBlocked ? `🔒 Заблокирован${blockedText}` : '✅ Активен'}
                    </span>
                </div>
                <div class="user-info">
                    <div>📧 Email: ${user.email || 'не указан'}</div>
                    <div>🆔 ID: ${user.id}</div>
                    <div>📅 Регистрация: ${new Date(user.createdAt).toLocaleDateString('ru-RU')}</div>
                    ${user.lastSeen ? `<div>👁️ Был в сети: ${new Date(user.lastSeen).toLocaleString('ru-RU')}</div>` : ''}
                </div>
                <div class="user-actions">
                    ${!isCurrentlyBlocked ?
                `<button class="btn-small block-user-btn" onclick="adminManager.showBlockModal(${user.id}, '${user.username}')">
                            🔒 Заблокировать
                        </button>` :
                `<button class="btn-small unblock-user-btn" onclick="adminManager.unblockUser(${user.id}, '${user.username}')">
                            🔓 Разблокировать
                        </button>`
            }
                </div>
            </div>
            `;
        }).join('');

        console.log("✅ Пользователи отображены в интерфейсе");
    },

    // 8. Проверка, заблокирован ли пользователь в данный момент
    isUserCurrentlyBlocked(user) {
        if (!user.blocked && !user.isBlocked) return false;

        if (user.blockedUntil) {
            const blockedUntil = new Date(user.blockedUntil);
            const now = new Date();
            return blockedUntil > now;
        }

        return true;
    },

    // 9. Показать модальное окно блокировки
    showBlockModal(userId, username) {
        this.currentBlockingUser = { id: userId, username: username };

        const modal = document.getElementById('blockModal');
        if (!modal) {
            this.createBlockModal();
        }

        document.getElementById('blockUserInfo').innerHTML = `
            <p><strong>Пользователь:</strong> ${username}</p>
            <p><strong>ID:</strong> ${userId}</p>
        `;

        document.getElementById('blockReason').value = '';
        document.getElementById('blockDuration').value = '1';
        document.querySelector('input[name="blockType"][value="temporary"]').checked = true;
        document.getElementById('durationGroup').style.display = 'block';

        modal.classList.remove('hidden');
    },

    // 10. Создать модальное окно блокировки
    createBlockModal() {
        const modalHTML = `
        <div id="blockModal" class="modal hidden">
            <div class="modal-content">
                <h3>🔒 Блокировка пользователя</h3>
                
                <div id="blockUserInfo" class="user-info-block">
                    <!-- Информация о пользователе -->
                </div>
                
                <div class="form-group">
                    <label>Причина блокировки:</label>
                    <textarea id="blockReason" rows="3" placeholder="Введите причину блокировки..." required></textarea>
                </div>
                
                <div class="form-group">
                    <label>Тип блокировки:</label>
                    <div class="radio-group">
                        <label>
                            <input type="radio" name="blockType" value="temporary" checked>
                            Временная блокировка
                        </label>
                        <label>
                            <input type="radio" name="blockType" value="permanent">
                            Постоянная блокировка
                        </label>
                    </div>
                </div>
                
                <div class="form-group" id="durationGroup">
                    <label for="blockDuration">Количество дней:</label>
                    <input type="number" id="blockDuration" value="1" min="1" max="365">
                </div>
                
                <div class="modal-actions">
                    <button type="button" class="btn-secondary" onclick="adminManager.hideBlockModal()">Отмена</button>
                    <button type="button" class="btn-primary" onclick="adminManager.confirmBlock()">Заблокировать</button>
                </div>
            </div>
        </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHTML);

        document.querySelectorAll('input[name="blockType"]').forEach(radio => {
            radio.addEventListener('change', function() {
                document.getElementById('durationGroup').style.display =
                    this.value === 'temporary' ? 'block' : 'none';
            });
        });
    },

    // 11. Скрыть модальное окно
    hideBlockModal() {
        const modal = document.getElementById('blockModal');
        if (modal) modal.classList.add('hidden');
        this.currentBlockingUser = null;
    },

    // 12. Подтверждение блокировки
    async confirmBlock() {
        const user = this.currentBlockingUser;
        if (!user) return;

        const blockType = document.querySelector('input[name="blockType"]:checked')?.value;
        const reason = document.getElementById('blockReason').value.trim();
        const duration = parseInt(document.getElementById('blockDuration').value);

        if (!blockType) {
            this.showError('Выберите тип блокировки');
            return;
        }

        if (!reason) {
            this.showError('Введите причину блокировки');
            return;
        }

        if (blockType === 'temporary' && (!duration || duration < 1)) {
            this.showError('Укажите корректное количество дней');
            return;
        }

        const confirmMessage = blockType === 'permanent'
            ? `Вы уверены, что хотите заблокировать пользователя ${user.username} навсегда?\nПричина: ${reason}`
            : `Вы уверены, что хотите заблокировать пользователя ${user.username} на ${duration} дней?\nПричина: ${reason}`;

        if (!confirm(confirmMessage)) return;

        try {
            console.log(`🔄 Блокировка пользователя ${user.id}...`);

            const response = await fetch(`/api/admin/users/${user.id}/block`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.checkAdminAuth()}`
                },
                body: JSON.stringify({
                    type: blockType,
                    reason: reason,
                    days: duration,
                    timestamp: new Date().toISOString()
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.success) {
                console.log(`✅ Пользователь ${user.username} заблокирован`);
                this.showSuccess('Пользователь заблокирован');
                this.hideBlockModal();

                // Обновляем локальные данные
                const userToBlock = this.users.find(u => u.id === user.id);
                if (userToBlock) {
                    userToBlock.blocked = true;
                    userToBlock.isBlocked = true;
                    if (blockType === 'temporary') {
                        const until = new Date();
                        until.setDate(until.getDate() + duration);
                        userToBlock.blockedUntil = until.toISOString();
                    } else {
                        userToBlock.blockedUntil = null;
                    }
                }

                this.displayUsers();
                this.notifyUser(user.id, 'blocked', blockType, duration);

            } else {
                console.error('❌ Ошибка блокировки:', data.message);
                this.showError('Ошибка блокировки: ' + (data.message || 'Неизвестная ошибка'));
            }

        } catch (error) {
            console.error('❌ Ошибка сети:', error);
            this.showError('Ошибка соединения с сервером');
        }
    },

    // 13. Разблокировка пользователя
    async unblockUser(userId, username) {
        if (!confirm(`Разблокировать пользователя ${username}?`)) return;

        try {
            console.log(`🔄 Разблокировка пользователя ${userId}...`);

            const response = await fetch(`/api/admin/users/${userId}/unblock`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.checkAdminAuth()}`
                },
                body: JSON.stringify({
                    timestamp: new Date().toISOString()
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.success) {
                console.log(`✅ Пользователь ${username} разблокирован`);
                this.showSuccess('Пользователь разблокирован');

                // Обновляем локальные данные
                const userToUnblock = this.users.find(u => u.id === userId);
                if (userToUnblock) {
                    userToUnblock.blocked = false;
                    userToUnblock.isBlocked = false;
                    userToUnblock.blockedUntil = null;
                }

                this.displayUsers();
                this.notifyUser(userId, 'unblocked');

            } else {
                console.error('❌ Ошибка разблокировки:', data.message);
                this.showError('Ошибка разблокировки: ' + (data.message || 'Неизвестная ошибка'));
            }

        } catch (error) {
            console.error('❌ Ошибка сети:', error);
            this.showError('Ошибка соединения с сервером');
        }
    },

    // 14. Уведомление пользователя через WebSocket или API
    async notifyUser(userId, action, blockType = null, days = null) {
        try {
            console.log(`📢 Отправка уведомления пользователю ${userId}: ${action}`);

            const response = await fetch(`/api/admin/users/${userId}/notify`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.checkAdminAuth()}`
                },
                body: JSON.stringify({
                    action: action,
                    blockType: blockType,
                    days: days,
                    timestamp: new Date().toISOString()
                })
            });

            if (response.ok) {
                const data = await response.json();
                if (data.success) {
                    console.log(`✅ Уведомление отправлено пользователю ${userId}`);
                }
            }
        } catch (error) {
            console.warn(`⚠️ Не удалось отправить уведомление: ${error.message}`);
        }
    },

    // 15. Показать сообщение об успехе
    showSuccess(message) {
        alert(`✅ ${message}`);
    },

    // 16. Показать сообщение об ошибке
    showError(message) {
        alert(`❌ ${message}`);
    }
};

// 17. Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    console.log("📄 Страница админа загружена");

    // Проверка авторизации
    if (!AdminManager.checkAdminAuth()) {
        console.error('❌ Нет токена авторизации');
        window.location.href = '/admin/login';
        return;
    }

    window.adminManager = AdminManager.init();
});

// 18. Глобальные функции для совместимости
window.loadAdminUsers = () => adminManager.renderUsers();
window.showUsers = () => adminManager.displayUsers();