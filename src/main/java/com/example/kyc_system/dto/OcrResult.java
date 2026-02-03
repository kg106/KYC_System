package com.example.kyc_system.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrResult {
    private String name;
    private String dob;
    private String documentNumber;
    private Map<String, Object> rawResponse;
}
