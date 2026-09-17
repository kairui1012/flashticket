package com.flashticket.authservice.dto;

import com.flashticket.authservice.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String id;
    private String email;
    private UserStatus status;
    private String accessToken;

}
