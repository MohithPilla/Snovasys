/**
 * Admin Portal Module
 * User management (Block/Unblock), suspicious activity monitor, and immutable audit logs.
 */

let allAuditLogs = [];

document.addEventListener('DOMContentLoaded', async () => {
  const user = requireAuth(['ROLE_ADMIN']);
  if (!user) return;

  document.getElementById('navAdminName').textContent = user.name;

  await Promise.all([
    loadUsers(),
    loadAuditLogs(),
    loadSuspiciousActivities()
  ]);

  // Audit filter change
  document.getElementById('auditFilter').addEventListener('change', (e) => {
    filterAuditLogs(e.target.value);
  });
});

async function loadUsers() {
  try {
    const users = await apiFetch('/admin/users');
    const tbody = document.getElementById('usersTableBody');
    document.getElementById('totalUsersCount').textContent = users.length;

    tbody.innerHTML = users.map(u => {
      const isBlocked = u.accountStatus === 'BLOCKED' || u.userStatus === 'BLOCKED';
      const statusBadge = isBlocked
        ? '<span class="badge badge-blocked">BLOCKED</span>'
        : '<span class="badge badge-active">ACTIVE</span>';

      const isCurrentAdmin = u.email === getUser().email;

      const actionBtn = isCurrentAdmin
        ? '<span class="text-muted small">Current Admin</span>'
        : (isBlocked
            ? `<button class="btn btn-sm btn-outline-success" onclick="toggleStatus(${u.userId}, 'ACTIVE')">Unblock</button>`
            : `<button class="btn btn-sm btn-outline-danger" onclick="toggleStatus(${u.userId}, 'BLOCKED')">Block</button>`
          );

      return `
        <tr>
          <td><small class="text-muted font-monospace">${u.userId}</small></td>
          <td><strong>${u.name}</strong><br><small class="text-muted">${u.email}</small></td>
          <td><span class="badge bg-secondary-subtle text-secondary">${u.role}</span></td>
          <td class="font-monospace">${u.accountNumber}</td>
          <td class="fw-bold font-monospace">$${parseFloat(u.balance).toFixed(2)}</td>
          <td>${statusBadge}</td>
          <td>${actionBtn}</td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    console.error('Error loading users:', err);
    alert('Failed to load user list: ' + err.message);
  }
}

async function toggleStatus(userId, newStatus) {
  const actionName = newStatus === 'BLOCKED' ? 'BLOCK' : 'UNBLOCK';
  if (!confirm(`Are you sure you want to ${actionName} this user account?`)) {
    return;
  }

  try {
    await apiFetch(`/admin/users/${userId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status: newStatus })
    });

    await loadUsers();
    await loadAuditLogs();
    await loadSuspiciousActivities();
  } catch (err) {
    alert(`Failed to update status: ` + err.message);
  }
}

async function loadAuditLogs() {
  try {
    allAuditLogs = await apiFetch('/admin/audit-logs');
    filterAuditLogs(document.getElementById('auditFilter').value);
  } catch (err) {
    console.error('Error loading audit logs:', err);
  }
}

function filterAuditLogs(filter) {
  const tbody = document.getElementById('auditTableBody');
  let filtered = allAuditLogs;

  if (filter !== 'ALL') {
    filtered = allAuditLogs.filter(log => log.action === filter);
  }

  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No audit records match the selected filter.</td></tr>';
    return;
  }

  tbody.innerHTML = filtered.map(log => {
    let actionBadge = 'bg-secondary';
    if (log.action.includes('SUCCESS')) actionBadge = 'bg-success';
    if (log.action.includes('FAILED')) actionBadge = 'bg-danger';
    if (log.action.includes('BLOCKED')) actionBadge = 'bg-warning text-dark';
    if (log.action.includes('DUPLICATE')) actionBadge = 'bg-danger';

    return `
      <tr>
        <td><small class="text-muted font-monospace">${log.id}</small></td>
        <td><small class="font-monospace text-primary">Actor #${log.actorId}</small></td>
        <td><span class="badge ${actionBadge}">${log.action}</span></td>
        <td>${log.description}</td>
        <td class="small text-muted">${new Date(log.timestamp).toLocaleString()}</td>
      </tr>
    `;
  }).join('');
}

async function loadSuspiciousActivities() {
  try {
    const suspicious = await apiFetch('/admin/suspicious-activities');
    const container = document.getElementById('suspiciousList');
    document.getElementById('suspiciousCount').textContent = suspicious.length;

    if (suspicious.length === 0) {
      container.innerHTML = '<div class="text-success p-3 small"><i class="bi bi-shield-check"></i> No suspicious activities detected. All system operations are nominal.</div>';
      return;
    }

    container.innerHTML = suspicious.slice(0, 10).map(s => `
      <div class="list-group-item list-group-item-action d-flex justify-content-between align-items-start py-2">
        <div class="ms-2 me-auto">
          <div class="fw-bold text-danger"><i class="bi bi-exclamation-triangle me-1"></i> ${s.action}</div>
          <small class="text-dark">${s.description}</small>
        </div>
        <span class="badge bg-light text-muted small">${new Date(s.timestamp).toLocaleTimeString()}</span>
      </div>
    `).join('');
  } catch (err) {
    console.error('Error loading suspicious activities:', err);
  }
}
