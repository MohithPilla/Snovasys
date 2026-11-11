/**
 * User Dashboard Module
 */

document.addEventListener('DOMContentLoaded', async () => {
  const user = requireAuth(['ROLE_USER', 'ROLE_ADMIN']);
  if (!user) return;

  // Set header user info
  document.getElementById('navUserName').textContent = user.name;
  if (user.role === 'ROLE_ADMIN') {
    const adminLink = document.getElementById('navAdminLink');
    if (adminLink) adminLink.classList.remove('d-none');
  }

  await loadDashboardData();

  // Refresh balance button
  const refreshBtn = document.getElementById('btnRefresh');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', async () => {
      refreshBtn.disabled = true;
      refreshBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span>';
      await loadDashboardData();
      refreshBtn.disabled = false;
      refreshBtn.innerHTML = '<i class="bi bi-arrow-clockwise"></i> Refresh';
    });
  }
});

async function loadDashboardData() {
  try {
    const [profile, transactions] = await Promise.all([
      apiFetch('/user/profile'),
      apiFetch('/user/transactions')
    ]);

    // Update Profile / Balance cards
    document.getElementById('accHolderName').textContent = profile.name;
    document.getElementById('accNumber').textContent = profile.accountNumber;
    document.getElementById('accStatusBadge').textContent = profile.accountStatus;
    document.getElementById('accStatusBadge').className = profile.accountStatus === 'ACTIVE'
      ? 'badge bg-success-subtle text-success border border-success'
      : 'badge bg-danger-subtle text-danger border border-danger';

    const formattedBalance = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(profile.balance);
    document.getElementById('accBalance').textContent = formattedBalance;

    renderTransactions(transactions, profile.accountNumber);
  } catch (err) {
    console.error('Failed to load dashboard data:', err);
    alert('Failed to load account data: ' + err.message);
  }
}

function renderTransactions(transactions, myAccountNumber) {
  const tbody = document.getElementById('txTableBody');
  const emptyState = document.getElementById('txEmptyState');

  if (!transactions || transactions.length === 0) {
    tbody.innerHTML = '';
    emptyState.classList.remove('d-none');
    return;
  }

  emptyState.classList.add('d-none');
  tbody.innerHTML = transactions.map(tx => {
    const isDebit = tx.senderAccount && tx.senderAccount.accountNumber === myAccountNumber;
    const amountClass = isDebit ? 'text-danger font-monospace' : 'text-success font-monospace';
    const amountPrefix = isDebit ? '-' : '+';
    const otherAccount = isDebit
      ? (tx.receiverAccount ? tx.receiverAccount.accountNumber : 'N/A')
      : (tx.senderAccount ? tx.senderAccount.accountNumber : 'N/A');
    const txType = isDebit ? 'Transfer to' : 'Received from';

    const dateFormatted = new Date(tx.createdAt).toLocaleString();

    return `
      <tr>
        <td><small class="text-muted font-monospace">${tx.id}</small></td>
        <td>
          <span class="badge ${isDebit ? 'bg-danger-subtle text-danger' : 'bg-success-subtle text-success'}">
            ${isDebit ? 'DEBIT' : 'CREDIT'}
          </span>
          <span class="ms-1 small">${txType} <strong>${otherAccount}</strong></span>
        </td>
        <td class="text-end ${amountClass} fw-bold">
          ${amountPrefix}$${parseFloat(tx.amount).toFixed(2)}
        </td>
        <td>
          <span class="badge ${tx.status === 'SUCCESS' ? 'badge-success-tx' : 'badge-failed-tx'}">
            ${tx.status}
          </span>
        </td>
        <td class="small text-muted">${dateFormatted}</td>
      </tr>
    `;
  }).join('');
}
