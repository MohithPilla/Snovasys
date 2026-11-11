/**
 * Authentication module (Login & Registration)
 */

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('loginForm');
  const registerForm = document.getElementById('registerForm');
  const authAlert = document.getElementById('authAlert');

  // Check URL params for session expiration
  const params = new URLSearchParams(window.location.search);
  if (params.get('expired') && authAlert) {
    showAlert('Your session has expired. Please log in again.', 'warning');
  }

  // Pre-fill demo buttons
  document.querySelectorAll('.btn-demo-fill').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const email = btn.getAttribute('data-email');
      const pass = btn.getAttribute('data-pass');
      document.getElementById('email').value = email;
      document.getElementById('password').value = pass;
    });
  });

  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      const submitBtn = loginForm.querySelector('button[type="submit"]');

      try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Logging in...';

        const data = await apiFetch('/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email, password })
        });

        setAuth(data);

        showAlert('Login successful! Redirecting...', 'success');
        setTimeout(() => {
          if (data.role === 'ROLE_ADMIN') {
            window.location.href = 'admin.html';
          } else {
            window.location.href = 'dashboard.html';
          }
        }, 600);
      } catch (err) {
        showAlert(err.message || 'Login failed. Please check your credentials.', 'danger');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Sign In';
      }
    });
  }

  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const name = document.getElementById('name').value.trim();
      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      const confirmPassword = document.getElementById('confirmPassword').value;
      const submitBtn = registerForm.querySelector('button[type="submit"]');

      if (password !== confirmPassword) {
        showAlert('Passwords do not match.', 'danger');
        return;
      }

      try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Registering...';

        const data = await apiFetch('/auth/register', {
          method: 'POST',
          body: JSON.stringify({ name, email, password })
        });

        setAuth(data);

        showAlert('Account created with $1,000 demo balance! Redirecting...', 'success');
        setTimeout(() => {
          window.location.href = 'dashboard.html';
        }, 800);
      } catch (err) {
        showAlert(err.message || 'Registration failed.', 'danger');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Create Account';
      }
    });
  }

  function showAlert(msg, type) {
    if (!authAlert) return;
    authAlert.className = `alert alert-${type} py-2 mb-3`;
    authAlert.textContent = msg;
    authAlert.classList.remove('d-none');
  }
});
