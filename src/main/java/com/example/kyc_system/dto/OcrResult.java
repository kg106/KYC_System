package com.example.kyc_system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.Map;

/**
 * Result of an OCR (Optical Character Recognition) process.
 * Contains extracted text fields from a document image.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrResult {
    @Schema(example = "JOHN DOE")
    private String name;
    @Schema(example = "1990-01-01")
    private String dob;
    @Schema(example = "ABCDE1234F")
    private String documentNumber;
    private Map<String, Object> rawResponse;
}
