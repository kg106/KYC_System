package com.example.kyc_system.security;

import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom implementation of Spring Security's UserDetailsService.
 * Responsible for loading user details from the database by email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

        private final UserRepository userRepository;

        /**
         * Loads the user's details from the database using their email as the username.
         * Maps the user's Roles to Spring Security GrantedAuthorities.
         *
         * @param email the email of the user to load
         * @return UserDetails object containing the user's security information
         * @throws UsernameNotFoundException if no user is found with the given email
         */
        @Override
        @Transactional
        public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
                log.debug("Loading user by email: {}", email);
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> {
                                        log.warn("User not found with email: {}", email);
                                        return new UsernameNotFoundException("User not found with email: " + email);
                                });

                log.info("User found: email={}, id={}, isActive={}", user.getEmail(), user.getId(), user.getIsActive());

                Set<GrantedAuthority> authorities = user.getUserRoles().stream()
                                .map(ur -> new SimpleGrantedAuthority(ur.getRole().getName()))
                                .collect(Collectors.toSet());

                return new org.springframework.security.core.userdetails.User(
                                user.getEmail(),
                                user.getPasswordHash(),
                                user.getIsActive(),
                                true,
                                true,
                                true,
                                authorities);
        }
}
