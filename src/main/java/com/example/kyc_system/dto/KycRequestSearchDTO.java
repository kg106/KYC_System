package com.example.kyc_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRequestSearchDTO {
    private Long userId;
    private String userName;
    private String status;
    private String documentType;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}
