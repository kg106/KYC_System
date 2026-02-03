package com.example.kyc_system.service;

import com.example.kyc_system.entity.User;

public interface UserService {
    User getActiveUser(Long userId);
}
