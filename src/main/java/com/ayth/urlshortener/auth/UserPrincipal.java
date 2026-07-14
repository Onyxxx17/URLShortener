package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps the {@link User} entity as a Spring Security {@link UserDetails}.
 * Stored inside the {@link org.springframework.security.core.Authentication}
 * set in the {@link org.springframework.security.core.context.SecurityContext}
 * by {@link JwtAuthenticationFilter} on every authenticated request.
 */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    /** Returns the underlying domain User — used by controllers/services. */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /** Email is used as the Spring Security username. */
    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
