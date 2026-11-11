/**
 * Money Transfer Module
 * Incorporates real-time receiver lookup, client-side sanity checks,
 * cryptographic idempotency key generation, and confirmation modal.
 */

let currentBalance = 0;
let myAccountNumber = '';
let verifiedReceiver = null;
let currentIdempotencyKey = '';

document.addEventListener('DOMContentLoaded', async () => {
  const user = requireAuth(['ROLE_USER', 'ROLE_ADMIN']);
  if (!user) return;

  document.getElementById('navUserName').textContent = user.name;
  if (user.role === 'ROLE_ADMIN') {
    const adminLink = document.getElementById('navAdminLink');
    if (adminLink) adminLink.classList.remove('d-none');
  }

  // Fetch current user account details
  try {
    const profile = await apiFetch('/user/profile');
    currentBalance = parseFloat(profile.balance);
    myAccountNumber = profile.accountNumber;

    document.getElementById('senderAccNum').textContent = myAccountNumber;
    document.getElementById('availBalance').textContent = `$${currentBalance.toFixed(2)}`;
  } catch (err) {
    alert('Failed to load account information: ' + err.message);
  }

  // Setup Idempotency Key
  resetIdempotencyKey();

  // Receiver account lookup
  const receiverInput = document.getElementById('receiverAcc');
  receiverInput.addEventListener('blur', lookupReceiver);

  // Form submit -> triggers Confirmation Modal
  const transferForm = document.getElementById('transferForm');
  transferForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const amount = parseFloat(document.getElementById('transferAmount').value);
    const receiver = receiverInput.value.trim();

    if (!receiver) {
      showTransferAlert('Please enter a receiver account number.', 'danger');
      return;
    }

    if (receiver === myAccountNumber) {
      showTransferAlert('Self-transfers are not allowed. Please enter a different account number.', 'danger');
      return;
    }

    if (isNaN(amount) || amount <= 0) {
      showTransferAlert('Transfer amount must be strictly greater than $0.00.', 'danger');
      return;
    }

    if (amount > currentBalance) {
      showTransferAlert(`Insufficient funds! Your available balance is $${currentBalance.toFixed(2)}.`, 'danger');
      return;
    }

    // Populate confirmation modal
    document.getElementById('modalSender').textContent = myAccountNumber;
    document.getElementById('modalReceiver').textContent = receiver;
    document.getElementById('modalReceiverName').textContent = verifiedReceiver ? verifiedReceiver.accountHolderName : 'Verified Account';
    document.getElementById('modalAmount').textContent = `$${amount.toFixed(2)}`;
    document.getElementById('modalIdempotency').textContent = currentIdempotencyKey.substring(0, 13) + '...';

    const confirmModal = new bootstrap.Modal(document.getElementById('confirmationModal'));
    confirmModal.show();
  });

  // Final Confirmation Button
  document.getElementById('btnConfirmTransfer').addEventListener('click', executeTransfer);
});

function resetIdempotencyKey() {
  currentIdempotencyKey = 'idemp_' + crypto.randomUUID();
  const idempEl = document.getElementById('idempotencyKeyDisplay');
  if (idempEl) idempEl.textContent = currentIdempotencyKey;
}

async function lookupReceiver() {
  const receiverInput = document.getElementById('receiverAcc');
  const receiverNumber = receiverInput.value.trim();
  const lookupFeedback = document.getElementById('receiverFeedback');

  if (!receiverNumber) {
    lookupFeedback.textContent = '';
    verifiedReceiver = null;
    return;
  }

  if (receiverNumber === myAccountNumber) {
    lookupFeedback.className = 'form-text text-danger';
    lookupFeedback.textContent = 'You cannot transfer funds to your own account.';
    verifiedReceiver = null;
    return;
  }

  try {
    lookupFeedback.className = 'form-text text-muted';
    lookupFeedback.textContent = 'Verifying account...';

    const account = await apiFetch(`/user/lookup/${receiverNumber}`);
    verifiedReceiver = account;

    if (account.status !== 'ACTIVE') {
      lookupFeedback.className = 'form-text text-danger';
      lookupFeedback.textContent = `Account found, but status is ${account.status} (Transfers blocked).`;
    } else {
      lookupFeedback.className = 'form-text text-success fw-bold';
      lookupFeedback.innerHTML = `<i class="bi bi-check-circle-fill"></i> Verified: ${account.accountHolderName}`;
    }
  } catch (err) {
    verifiedReceiver = null;
    lookupFeedback.className = 'form-text text-danger';
    lookupFeedback.textContent = 'Account number not found.';
  }
}

async function executeTransfer() {
  const confirmBtn = document.getElementById('btnConfirmTransfer');
  const amount = parseFloat(document.getElementById('transferAmount').value);
  const receiver = document.getElementById('receiverAcc').value.trim();

  try {
    confirmBtn.disabled = true;
    confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Processing Secure Transfer...';

    const response = await apiFetch('/transfer', {
      method: 'POST',
      body: JSON.stringify({
        receiverAccountNumber: receiver,
        amount: amount,
        idempotencyKey: currentIdempotencyKey
      })
    });

    // Close Confirmation Modal
    bootstrap.Modal.getInstance(document.getElementById('confirmationModal')).hide();

    // Show Success Receipt
    document.getElementById('receiptTxId').textContent = response.transactionId;
    document.getElementById('receiptAmount').textContent = `$${parseFloat(response.amount).toFixed(2)}`;
    document.getElementById('receiptReceiver').textContent = `${response.receiverName} (${response.receiverAccountNumber})`;
    document.getElementById('receiptTimestamp').textContent = new Date(response.timestamp).toLocaleString();

    const receiptModal = new bootstrap.Modal(document.getElementById('receiptModal'));
    receiptModal.show();

    // Reset Form & regenerate idempotency key for next transfer
    document.getElementById('transferForm').reset();
    document.getElementById('receiverFeedback').textContent = '';
    resetIdempotencyKey();

    // Update displayed balance
    currentBalance -= amount;
    document.getElementById('availBalance').textContent = `$${currentBalance.toFixed(2)}`;
  } catch (err) {
    bootstrap.Modal.getInstance(document.getElementById('confirmationModal')).hide();
    showTransferAlert(err.message || 'Transfer failed.', 'danger');
    resetIdempotencyKey();
  } finally {
    confirmBtn.disabled = false;
    confirmBtn.innerHTML = 'Confirm & Transfer';
  }
}

function showTransferAlert(msg, type) {
  const alertEl = document.getElementById('transferAlert');
  alertEl.className = `alert alert-${type} py-2 mb-3`;
  alertEl.textContent = msg;
  alertEl.classList.remove('d-none');
}
