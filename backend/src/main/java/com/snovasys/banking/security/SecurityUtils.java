package com.snovasys.banking.security;

import com.snovasys.banking.entity.User;
import com.snovasys.banking.exception.UnauthorizedException;
import com.snovasys.banking.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Security utility class to extract authenticated user credentials strictly from
 * the server-side SecurityContextHolder.
 *
 * CRITICAL SECURITY PRINCIPLE:
 * We NEVER trust any user ID, sender ID, actor ID, or role sent via request body,
 * path variables, or query parameters. The authenticated identity is established
 * exclusively via cryptographic verification of the JWT / session context.
 */
@Component
public class SecurityUtils {

    private final UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public static String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal instanceof String && !"anonymousUser".equals(principal)) {
            return (String) principal;
        }

        throw new UnauthorizedException("No authenticated principal found in security context");
    }

    public User getCurrentUser() {
        String email = getAuthenticatedEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found in database: " + email));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
