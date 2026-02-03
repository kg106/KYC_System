package com.example.kyc_system.service;

import com.example.kyc_system.entity.KycExtractedData;
import com.example.kyc_system.entity.KycVerificationResult;
import com.example.kyc_system.entity.User;

public interface KycVerificationService {

    KycVerificationResult verifyAndSave(Long requestId, User user, KycExtractedData extractedData);
}

