package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private UserRepository userRepository;
    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void loadUserByUsername_returnsUserPrincipalWrappingTheFoundUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("found@example.com");
        user.setUsername("found");
        user.setPassword("hashed");
        when(userRepository.findByEmail("found@example.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("found@example.com");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) result).getUser()).isEqualTo(user);
        assertThat(result.getUsername()).isEqualTo("found@example.com");
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenNoUserMatches() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
