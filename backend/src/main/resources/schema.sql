-- ====================================================================
-- SECURE MONEY TRANSFER AND TRANSACTION INTEGRITY SYSTEM
-- Database Schema for MySQL 8 / 9
-- ====================================================================

CREATE DATABASE IF NOT EXISTS snovasys_banking;
USE snovasys_banking;

-- Table: users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: accounts
CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_accounts_account_number (account_number),
    INDEX idx_accounts_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: transactions
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_account_id BIGINT NOT NULL,
    receiver_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_sender FOREIGN KEY (sender_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_receiver FOREIGN KEY (receiver_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    INDEX idx_tx_sender (sender_account_id),
    INDEX idx_tx_receiver (receiver_account_id),
    INDEX idx_tx_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table: audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_tx FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL,
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
