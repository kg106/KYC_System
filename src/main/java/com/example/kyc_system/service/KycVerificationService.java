package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycExtractedData;
import com.example.kyc_system.entity.KycVerificationResult;

public interface KycVerificationService {

    KycVerificationResult verifyAndSave(Long requestId, KycExtractedData extractedData);
}
