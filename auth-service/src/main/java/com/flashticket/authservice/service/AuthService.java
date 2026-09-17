package com.flashticket.authservice.service;

import com.flashticket.authservice.dto.AuthResponse;
import com.flashticket.authservice.dto.LoginRequest;
import com.flashticket.authservice.dto.RegisterRequest;
import com.flashticket.authservice.entity.AuthAccount;
import com.flashticket.authservice.entity.UserStatus;
import com.flashticket.authservice.mapper.AuthAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j

public class AuthService {

    private final AuthAccountMapper authAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthResponse login(LoginRequest loginRequest) {

        AuthAccount existingAccount =
                authAccountMapper.findByEmail(loginRequest.getEmail());

        if (existingAccount == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }
        if (!passwordEncoder.matches(
                loginRequest.getPassword(),
                existingAccount.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (existingAccount.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is not active"
            );
        }


        return mapToResponse(existingAccount,tokenService.generateToken(existingAccount));
    }

    public AuthResponse register(RegisterRequest registerRequest) {

        AuthAccount existingAccount =
                authAccountMapper.findByEmail(registerRequest.getEmail());

        if (existingAccount != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        String encodedPassword =
                passwordEncoder.encode(registerRequest.getPassword());

        AuthAccount account = new AuthAccount();
        account.setId(UUID.randomUUID().toString());
        account.setStatus(UserStatus.ACTIVE);
        account.setEmail(registerRequest.getEmail());
        account.setPassword(encodedPassword);
        account.setCreatedAt(LocalDateTime.now());
        authAccountMapper.insert(account);
        return mapToResponse(account,tokenService.generateToken(account));
    }

    private AuthResponse mapToResponse(
            AuthAccount account,
            String accessToken
    ) {
        AuthResponse response = new AuthResponse();

        response.setId(account.getId());
        response.setEmail(account.getEmail());
        response.setStatus(account.getStatus());
        response.setAccessToken(accessToken);

        return response;
    }
}
