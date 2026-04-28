package com.example.kyc_system.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a user role (e.g., "ROLE_USER", "ROLE_ADMIN").
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    /**
     * Unique identifier for the role.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name of the role.
     */
    @Column(nullable = false, length = 100, unique = true)
    private String name;

}
