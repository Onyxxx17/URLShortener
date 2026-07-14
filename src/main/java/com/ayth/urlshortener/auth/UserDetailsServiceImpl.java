package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Plugs the application's {@link User} entity into Spring Security's
 * authentication pipeline. Spring Security calls {@link #loadUserByUsername}
 * (passing the email) during {@code AuthenticationManager.authenticate()}.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));

        return new UserPrincipal(user);
    }
}
