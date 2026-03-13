package com.example.kyc_system.util;

import com.example.kyc_system.config.KycProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KycFileValidator Unit Tests")
class KycFileValidatorTest {

    @Mock
    private KycProperties kycProperties;

    @Mock
    private KycProperties.File fileConfig;

    @InjectMocks
    private KycFileValidator fileValidator;

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "application/pdf");

    @BeforeEach
    void setUp() {
        lenient().when(kycProperties.getFile()).thenReturn(fileConfig);
        lenient().when(fileConfig.getAllowedTypes()).thenReturn(ALLOWED_TYPES);
    }

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid files — should pass without exception")
    class ValidFileTests {

        @Test
        @DisplayName("Should accept image/jpeg")
        void validate_JpegFile_Passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "pan.jpg", "image/jpeg", "content".getBytes());

            assertDoesNotThrow(() -> fileValidator.validate(file));
        }

        @Test
        @DisplayName("Should accept image/png")
        void validate_PngFile_Passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "aadhaar.png", "image/png", "content".getBytes());

            assertDoesNotThrow(() -> fileValidator.validate(file));
        }

        @Test
        @DisplayName("Should accept application/pdf")
        void validate_PdfFile_Passes() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "passport.pdf", "application/pdf", "content".getBytes());

            assertDoesNotThrow(() -> fileValidator.validate(file));
        }
    }

    // ─── Empty / Null File ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Empty or null files — should throw IllegalArgumentException")
    class EmptyFileTests {

        @Test
        @DisplayName("Should throw when file is empty (0 bytes)")
        void validate_EmptyFile_ThrowsException() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> fileValidator.validate(emptyFile));

            assertTrue(ex.getMessage().contains("File is empty"));
        }

        @Test
        @DisplayName("Should throw when file is null")
        void validate_NullFile_ThrowsException() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> fileValidator.validate(null));

            assertTrue(ex.getMessage().contains("File is empty"));
        }
    }

    // ─── Disallowed MIME Types ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Disallowed MIME types — should throw IllegalArgumentException")
    class InvalidMimeTypeTests {

        @ParameterizedTest(name = "Should reject MIME type: {0}")
        @ValueSource(strings = { "text/plain", "application/zip", "video/mp4", "application/json", "text/html" })
        @DisplayName("Should reject disallowed MIME types")
        void validate_DisallowedMimeType_ThrowsException(String mimeType) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.xyz", mimeType, "content".getBytes());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> fileValidator.validate(file));

            assertTrue(ex.getMessage().contains("Invalid file type"),
                    "Error message should mention invalid file type");
            assertTrue(ex.getMessage().contains(mimeType),
                    "Error message should include the rejected MIME type");
        }

        @Test
        @DisplayName("Should throw when content type is null")
        void validate_NullContentType_ThrowsException() {
            // MockMultipartFile with null content type
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.jpg", null, "content".getBytes());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> fileValidator.validate(file));

            assertTrue(ex.getMessage().contains("Invalid file type"));
        }
    }

    // ─── Allowed Types List Interaction ───────────────────────────────────────

    @Nested
    @DisplayName("Allowed types list interaction")
    class AllowedTypesTests {

        @Test
        @DisplayName("Should read allowed types from KycProperties on each validation call")
        void validate_ReadsAllowedTypesFromProperties() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "pan.jpg", "image/jpeg", "content".getBytes());

            fileValidator.validate(file);

            verify(kycProperties, atLeastOnce()).getFile();
            verify(fileConfig, atLeastOnce()).getAllowedTypes();
        }

        @Test
        @DisplayName("Should use only the allowed types from config — not hardcoded list")
        void validate_UsesConfiguredAllowedTypes_NotHardcoded() {
            // Override to only allow PDF
            when(fileConfig.getAllowedTypes()).thenReturn(List.of("application/pdf"));

            MockMultipartFile jpeg = new MockMultipartFile(
                    "file", "pan.jpg", "image/jpeg", "content".getBytes());

            // JPEG should now be rejected because config only allows PDF
            assertThrows(IllegalArgumentException.class, () -> fileValidator.validate(jpeg));
        }
    }
}