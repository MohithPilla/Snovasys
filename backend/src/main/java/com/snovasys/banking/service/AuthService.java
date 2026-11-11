package com.snovasys.banking.service;

import com.snovasys.banking.dto.AuthRequest;
import com.snovasys.banking.dto.AuthResponse;
import com.snovasys.banking.dto.RegisterRequest;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.AccountStatus;
import com.snovasys.banking.entity.Role;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.exception.AccountInactiveException;
import com.snovasys.banking.exception.ResourceNotFoundException;
import com.snovasys.banking.exception.UnauthorizedException;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.UserRepository;
import com.snovasys.banking.security.JwtTokenProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;

@Service
public class AuthService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new IllegalArgumentException("Email is already registered: " + request.getEmail());
        }

        // 1. Create and save User with hashed password
        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        // 2. Generate a unique 10-digit account number
        String accountNumber = generateUniqueAccountNumber();

        // 3. Create Account with initial starting balance of 1,000.00
        BigDecimal initialBalance = new BigDecimal("1000.0000").setScale(4, RoundingMode.HALF_UP);
        Account account = new Account(savedUser, accountNumber, initialBalance, AccountStatus.ACTIVE);
        accountRepository.save(account);

        // 4. Log Audit Event
        auditService.logEvent(null, savedUser.getId(), "USER_REGISTERED",
                "New user registered: " + savedUser.getEmail() + " with account: " + accountNumber);

        // 5. Generate JWT token
        String token = tokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                savedUser.getName()
        );

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                accountNumber
        );
    }

    public AuthResponse login(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Authenticate credentials using Spring Security AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found with email: " + email));

        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            auditService.logEvent(null, user.getId(), "LOGIN_BLOCKED",
                    "Blocked user attempted login: " + user.getEmail());
            throw new AccountInactiveException("Your account has been blocked. Please contact system administration.");
        }

        Account account = accountRepository.findByUserId(user.getId())
                .orElse(null);

        String accountNumber = account != null ? account.getAccountNumber() : "N/A";

        // Audit login event
        auditService.logEvent(null, user.getId(), "USER_LOGIN",
                "User logged in successfully: " + user.getEmail());

        String token = tokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getName()
        );

        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                accountNumber
        );
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long number = 1000000000L + (long) (random.nextDouble() * 8999999999L);
            accountNumber = String.valueOf(number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    /**
     * Seeds initial Admin and Demo accounts if database is empty.
     */
    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            // Seed Admin
            User admin = new User(
                    "System Admin",
                    "admin@bank.com",
                    passwordEncoder.encode("Admin@123"),
                    Role.ROLE_ADMIN,
                    AccountStatus.ACTIVE
            );
            User savedAdmin = userRepository.save(admin);
            Account adminAccount = new Account(savedAdmin, "1000000001", new BigDecimal("50000.0000"), AccountStatus.ACTIVE);
            accountRepository.save(adminAccount);

            // Seed Demo User 1 (Alice)
            User alice = new User(
                    "Alice Walker",
                    "alice@bank.com",
                    passwordEncoder.encode("Alice@123"),
                    Role.ROLE_USER,
                    AccountStatus.ACTIVE
            );
            User savedAlice = userRepository.save(alice);
            Account aliceAccount = new Account(savedAlice, "1000000002", new BigDecimal("2500.0000"), AccountStatus.ACTIVE);
            accountRepository.save(aliceAccount);

            // Seed Demo User 2 (Bob)
            User bob = new User(
                    "Bob Smith",
                    "bob@bank.com",
                    passwordEncoder.encode("Bob@123"),
                    Role.ROLE_USER,
                    AccountStatus.ACTIVE
            );
            User savedBob = userRepository.save(bob);
            Account bobAccount = new Account(savedBob, "1000000003", new BigDecimal("1500.0000"), AccountStatus.ACTIVE);
            accountRepository.save(bobAccount);

            auditService.logEvent(null, savedAdmin.getId(), "SYSTEM_INIT", "Initial seed data loaded (Admin, Alice, Bob)");
        }
    }
}
