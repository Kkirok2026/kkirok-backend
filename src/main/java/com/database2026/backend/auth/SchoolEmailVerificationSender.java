package com.database2026.backend.auth;

import java.time.LocalDateTime;

public interface SchoolEmailVerificationSender {

    void send(String email, String code, LocalDateTime expiresAt);
}
