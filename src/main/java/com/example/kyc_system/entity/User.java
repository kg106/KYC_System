package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a user in the KYC system.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /**
     * Primary key for the user.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The full name of the user.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The unique email address of the user.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * The unique mobile number of the user.
     */
    @Column(name = "mobile_number", nullable = false, unique = true, length = 15)
    private String mobileNumber;

    /**
     * The ID of the tenant the user belongs to.
     */
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /**
     * BCRYPT hashed password of the user.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Whether the user account is active.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * The date of birth of the user.
     */
    private LocalDate dob;

    /**
     * The timestamp of the user's last login.
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * The set of roles assigned to the user.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();

    /**
     * The set of KYC requests submitted by the user.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<KycRequest> kycRequests = new HashSet<>();

    /**
     * The tenant the user belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

}
