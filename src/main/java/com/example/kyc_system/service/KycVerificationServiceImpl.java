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
                        KycExtractedData extractedData) {

                KycRequest request = requestRepository.findById(requestId)
                                .orElseThrow(() -> new RuntimeException("Request not found"));
                User user = request.getUser();

                String userName = user.getName() != null ? user.getName().toLowerCase() : "";
                String extractedName = extractedData.getExtractedName() != null
                                ? extractedData.getExtractedName().toLowerCase()
                                : "";

                double nameScore = similarity(userName, extractedName);
                boolean nameMatch = nameScore >= 0.75; // More lenient for middle initials
                boolean dobMatch = user.getDob() != null && user.getDob().equals(extractedData.getExtractedDob());

                String storedDoc = extractedData.getKycDocument().getDocumentNumber();
                String storedDocNormalized = normalize(storedDoc);
                String extractedDocNormalized = normalize(extractedData.getExtractedDocumentNumber());

                boolean docMatch = storedDocNormalized != null &&
                                storedDocNormalized.equalsIgnoreCase(extractedDocNormalized);

                StringBuilder reason = new StringBuilder();
                if (!nameMatch)
                        reason.append(String.format("Name mismatch (score: %.2f%%). ", nameScore * 100));
                if (!dobMatch)
                        reason.append(String.format("DOB mismatch (Expected: %s, Found: %s). ", user.getDob(),
                                        extractedData.getExtractedDob()));
                if (!docMatch)
                        reason.append(String.format("Doc Number mismatch (Expected: %s, Found: %s). ", storedDoc,
                                        extractedData.getExtractedDocumentNumber()));

                KycVerificationResult result = KycVerificationResult.builder()
                                .kycRequest(request)
                                .nameMatchScore(BigDecimal.valueOf(nameScore * 100))
                                .dobMatch(dobMatch)
                                .documentNumberMatch(docMatch)
                                .finalStatus(nameMatch && dobMatch && docMatch
                                                ? KycStatus.VERIFIED.name()
                                                : KycStatus.FAILED.name())
                                .decisionReason(reason.toString().trim())
                                .build();

                return repository.save(result);
        }

        private String normalize(String s) {
                if (s == null)
                        return null;
                return s.replaceAll("[^A-Z0-9]", ""); // Keep only alphanumeric
        }

        private double similarity(String s1, String s2) {
                if (s1 == null || s2 == null)
                        return 0;
                if (s1.equalsIgnoreCase(s2))
                        return 1.0;

                int distance = getLevenshteinDistance(s1, s2);
                int maxLength = Math.max(s1.length(), s2.length());
                if (maxLength == 0)
                        return 1.0;

                return 1.0 - ((double) distance / maxLength);
        }

        private int getLevenshteinDistance(String s1, String s2) {
                int[][] dp = new int[s1.length() + 1][s2.length() + 1];

                for (int i = 0; i <= s1.length(); i++)
                        dp[i][0] = i;
                for (int j = 0; j <= s2.length(); j++)
                        dp[0][j] = j;

                for (int i = 1; i <= s1.length(); i++) {
                        for (int j = 1; j <= s2.length(); j++) {
                                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                                                dp[i - 1][j - 1] + cost);
                        }
                }
                return dp[s1.length()][s2.length()];
        }
}
