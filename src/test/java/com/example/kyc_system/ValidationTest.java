package com.example.kyc_system;

import com.example.kyc_system.dto.UserDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {

        private Validator validator;

        @BeforeEach
        void setUp() {
                ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
                validator = factory.getValidator();
        }

        @Test
        void testValidUserDTO() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("Password123!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertTrue(violations.isEmpty(), "Expected no violations for valid UserDTO");
        }

        @Test
        void testInvalidName() {
                UserDTO user = UserDTO.builder()
                                .name("12134abcd@")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("Password123!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty(), "Expected violations for invalid name");
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")),
                                "Expected violation for name field");
        }

        @Test
        void testInvalidEmail() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john@emailcom")
                                .mobileNumber("1234567890")
                                .password("Password123!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty(), "Expected violations for invalid email");
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                                "Expected violation for email field");
        }

        @Test
        void testInvalidMobileNumber() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("12345")
                                .password("Password123!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty(), "Expected violations for invalid mobile number");
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("mobileNumber")),
                                "Expected violation for mobileNumber field");
        }

        @Test
        void testPasswordMissingUppercase() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("password123!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty());
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
        }

        @Test
        void testPasswordMissingDigit() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("Password!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty());
        }

        @Test
        void testPasswordMissingSpecialChar() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("Password123")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty());
        }

        @Test
        void testPasswordTooShort() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("P1a!")
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty());
        }

        @Test
        void testFutureDob() {
                UserDTO user = UserDTO.builder()
                                .name("John Doe")
                                .email("john.doe@example.com")
                                .mobileNumber("1234567890")
                                .password("Password123!")
                                .dob(LocalDate.now().plusDays(1))
                                .build();

                Set<ConstraintViolation<UserDTO>> violations = validator.validate(user);
                assertFalse(violations.isEmpty(), "Expected violations for future DOB");
                assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dob")),
                                "Expected violation for dob field");
        }
}
