class AuthManager {
    constructor() {
        this.currentTab = 'login';
        this.init();
    }

    init() {
        this.setupTabSwitching();
        this.setupLoginForm();
        this.setupRegisterForm();
    }

    setupTabSwitching() {
        const tabButtons = document.querySelectorAll('.tab-button');
        const tabContents = document.querySelectorAll('.tab-content');

        tabButtons.forEach(button => {
            button.addEventListener('click', () => {
                const tab = button.dataset.tab;

                // Update buttons
                tabButtons.forEach(btn => btn.classList.remove('active'));
                button.classList.add('active');

                // Update contents
                tabContents.forEach(content => content.classList.remove('active'));
                document.getElementById(`${tab}Form`).classList.add('active');

                this.currentTab = tab;
            });
        });
    }

    setupLoginForm() {
        const form = document.getElementById('loginForm');
        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('loginUsername').value;
            const password = document.getElementById('loginPassword').value;

            await this.login(username, password);
        });
    }

    setupRegisterForm() {
        const form = document.getElementById('registerForm');
        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('registerUsername').value;
            const password = document.getElementById('registerPassword').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            if (password !== confirmPassword) {
                this.showMessage('registerMessage', 'Пароли не совпадают', 'error');
                return;
            }

            if (password.length < 3) {
                this.showMessage('registerMessage', 'Пароль должен содержать минимум 3 символа', 'error');
                return;
            }

            await this.register(username, password);
        });
    }

    async login(username, password) {
        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
            });

            const data = await response.json();

            if (data.success) {
                if (data.role === 'ADMIN') {
                    window.location.href = '/admin';
                } else {
                    window.location.href = '/chat';
                }
            } else {
                this.showMessage('loginMessage', data.message || 'Ошибка входа', 'error');
            }
        } catch (error) {
            console.error('Login error:', error);
            this.showMessage('loginMessage', 'Ошибка соединения', 'error');
        }
    }

    async register(username, password) {
        try {
            const response = await fetch('/api/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
            });

            const data = await response.json();

            if (data.success) {
                this.showMessage('registerMessage', 'Регистрация успешна! Теперь войдите в систему.', 'success');
                // Switch to login tab
                document.querySelector('[data-tab="login"]').click();
                document.getElementById('loginUsername').value = username;
            } else {
                this.showMessage('registerMessage', data.message || 'Ошибка регистрации', 'error');
            }
        } catch (error) {
            console.error('Register error:', error);
            this.showMessage('registerMessage', 'Ошибка соединения', 'error');
        }
    }

    showMessage(elementId, message, type) {
        const element = document.getElementById(elementId);
        element.textContent = message;
        element.className = `message ${type}`;
    }
}

// Initialize auth manager when page loads
document.addEventListener('DOMContentLoaded', () => {
    new AuthManager();
});