package com.ut.edu.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupResponse {

    private String secret;
    private String qrCodeDataUrl;
    private List<String> backupCodes;
    private String message;

    public static TwoFactorSetupResponse from(String secret, String qrCodeDataUrl, List<String> backupCodes) {
        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .qrCodeDataUrl(qrCodeDataUrl)
                .backupCodes(backupCodes)
                .message("Scan the QR code with Google Authenticator app and verify with a TOTP code to complete setup")
                .build();
    }
}
