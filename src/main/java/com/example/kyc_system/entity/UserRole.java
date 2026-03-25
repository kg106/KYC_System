package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Intermediate entity representing the many-to-many relationship between Users and Roles.
 */
@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole extends BaseEntity {

    /**
     * Unique identifier for the user-role mapping.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user associated with this role mapping.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The role associated with this user mapping.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}
