package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Plain unit test — no Spring context. JwtService and UserDetailsServiceImpl
 * are mocked so only doFilterInternal's own cookie/header/validity branching
 * is exercised. Security-critical: this filter decides who the rest of the
 * chain thinks is logged in.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserDetailsServiceImpl userDetailsService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsServiceImpl.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noTokenAtAll_proceedsAsAnonymous() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void validJwtCookie_authenticatesAsThatUser() throws Exception {
        request.setCookies(new Cookie("jwt", "cookie-token"));
        when(jwtService.isTokenValid("cookie-token")).thenReturn(true);
        when(jwtService.extractEmail("cookie-token")).thenReturn("user@example.com");
        UserPrincipal principal = principalWithAuthorities();
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        verify(chain).doFilter(request, response);
    }

    @Test
    void cookieTakesPriorityOverAuthorizationHeader() throws Exception {
        request.setCookies(new Cookie("jwt", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");
        when(jwtService.isTokenValid("cookie-token")).thenReturn(true);
        when(jwtService.extractEmail("cookie-token")).thenReturn("cookie-user@example.com");
        when(userDetailsService.loadUserByUsername("cookie-user@example.com")).thenReturn(principalWithAuthorities());

        filter.doFilterInternal(request, response, chain);

        verify(jwtService, never()).isTokenValid("header-token");
        verify(userDetailsService).loadUserByUsername("cookie-user@example.com");
    }

    @Test
    void noCookie_fallsBackToAuthorizationHeader() throws Exception {
        request.addHeader("Authorization", "Bearer header-token");
        when(jwtService.isTokenValid("header-token")).thenReturn(true);
        when(jwtService.extractEmail("header-token")).thenReturn("header-user@example.com");
        when(userDetailsService.loadUserByUsername("header-user@example.com")).thenReturn(principalWithAuthorities());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void authorizationHeaderWithoutBearerPrefix_isIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userDetailsService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_clearsContextAndProceedsAsAnonymous() throws Exception {
        request.setCookies(new Cookie("jwt", "bad-token"));
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenValidButUserNoLongerExists_clearsContextAndProceedsAsAnonymous() throws Exception {
        request.setCookies(new Cookie("jwt", "stale-token"));
        when(jwtService.isTokenValid("stale-token")).thenReturn(true);
        when(jwtService.extractEmail("stale-token")).thenReturn("deleted@example.com");
        when(userDetailsService.loadUserByUsername("deleted@example.com"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("gone"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticationAlreadySet_doesNotOverwriteIt() throws Exception {
        request.setCookies(new Cookie("jwt", "cookie-token"));
        when(jwtService.isTokenValid("cookie-token")).thenReturn(true);

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken existing =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("already-set", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existing);
        verify(jwtService, never()).extractEmail(any());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    private UserPrincipal principalWithAuthorities() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setUsername("user");
        user.setPassword("hashed");
        return new UserPrincipal(user);
    }
}
