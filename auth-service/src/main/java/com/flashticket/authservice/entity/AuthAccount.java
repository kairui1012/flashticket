package com.flashticket.authservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthAccount {
    private String id;
    private String email;
    private String password;
    private UserStatus status;
    private LocalDateTime createdAt;
}
