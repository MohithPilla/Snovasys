/**
 * Secure Money Transfer System - API Client
 * Centralized fetch wrapper handling JWT authentication, headers, and error parsing.
function getApiBase() {
  return localStorage.getItem('snovasys_api_url')
    || (window.location.origin.includes(':8080') ? '/api' : (window.API_BASE_URL || 'http://localhost:8080/api'));
}

function configureApiEndpoint() {
  const current = getApiBase();
  const input = prompt("Set Backend API URL (e.g., http://localhost:8080/api or https://your-backend.onrender.com/api):", current);
  if (input !== null) {
    if (input.trim() === '') {
      localStorage.removeItem('snovasys_api_url');
    } else {
      localStorage.setItem('snovasys_api_url', input.trim().replace(/\/+$/, ''));
    }
    alert('Backend API Base URL set to: ' + getApiBase());
    window.location.reload();
  }
}

function getToken() {
  return localStorage.getItem('jwt_token');
}

function setAuth(data) {
  localStorage.setItem('jwt_token', data.token);
  localStorage.setItem('user_info', JSON.stringify({
    userId: data.userId,
    name: data.name,
    email: data.email,
    role: data.role,
    accountNumber: data.accountNumber
  }));
}

function getUser() {
  const userStr = localStorage.getItem('user_info');
  return userStr ? JSON.parse(userStr) : null;
}

function clearAuth() {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_info');
}

function requireAuth(allowedRoles = []) {
  const token = getToken();
  const user = getUser();
  if (!token || !user) {
    window.location.href = 'login.html';
    return null;
  }
  if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
    alert('Access Denied: You do not have permissions for this page.');
    window.location.href = user.role === 'ROLE_ADMIN' ? 'admin.html' : 'dashboard.html';
    return null;
  }
  return user;
}

async function apiFetch(endpoint, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${getApiBase()}${endpoint}`, {
    ...options,
    headers
  });

  if (response.status === 401) {
    clearAuth();
    if (!window.location.pathname.endsWith('login.html')) {
      window.location.href = 'login.html?expired=true';
    }
  }

  let data;
  try {
    data = await response.json();
  } catch (e) {
    data = null;
  }

  if (!response.ok) {
    const errorMsg = (data && (data.message || data.error)) || `HTTP error! status: ${response.status}`;
    const err = new Error(errorMsg);
    err.status = response.status;
    err.data = data;
    throw err;
  }

  return data;
}

function logout() {
  clearAuth();
  window.location.href = 'login.html';
}
