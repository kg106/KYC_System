package com.example.kyc_system.service;

import com.example.kyc_system.entity.*;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.repository.KycVerificationResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class KycVerificationServiceImpl implements KycVerificationService {

        private final KycVerificationResultRepository repository;
        private final KycRequestRepository requestRepository;

        @Override
        public KycVerificationResult verifyAndSave(
                        Long requestId,
                        User user,
                        KycExtractedData extracted) {

                KycRequest request = requestRepository.findById(requestId)
                                .orElseThrow(() -> new RuntimeException("Request not found"));

                boolean nameMatch = similarity(user.getName(), extracted.getExtractedName()) > 0.8;
                boolean dobMatch = user.getDob().equals(extracted.getExtractedDob());
                boolean docMatch = extracted.getExtractedDocumentNumber() != null;

                KycVerificationResult result = KycVerificationResult.builder()
                                .kycRequest(request)
                                .nameMatchScore(BigDecimal.valueOf(nameMatch ? 100 : 0))
                                .dobMatch(dobMatch)
                                .documentNumberMatch(docMatch)
                                .finalStatus(nameMatch && dobMatch && docMatch
                                                ? KycStatus.VERIFIED.name()
                                                : KycStatus.FAILED.name())
                                .build();

                return repository.save(result);
        }

        private double similarity(String s1, String s2) {
                if (s1 == null || s2 == null)
                        return 0;
                if (s1.equalsIgnoreCase(s2))
                        return 1.0;
                // Simple placeholder similarity
                return 0.9;
        }
}
