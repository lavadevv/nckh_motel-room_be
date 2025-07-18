package com.nckh.motelroom.controller;

import com.nckh.motelroom.config.JwtConfig;
import com.nckh.motelroom.dto.entity.UserDto;
import com.nckh.motelroom.dto.request.*;
import com.nckh.motelroom.dto.response.BaseResponse;
import com.nckh.motelroom.dto.response.LoginResponse;
import com.nckh.motelroom.dto.response.RegisterResponse;
import com.nckh.motelroom.service.AuthenticateService;
import com.nckh.motelroom.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticateController {
    private final AuthenticateService authenticateService;
    private final UserService userService;
    private final JwtConfig jwtConfig;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request){
        LoginResponse response=authenticateService.login(request);
        return BaseResponse.successData(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request){
        RegisterResponse response=authenticateService.register(request);
        return BaseResponse.successData(response);
    }

    @PutMapping("verify-account")
    public ResponseEntity<?> verifyAccount(@Valid @RequestBody VerifyAccountRequest request) {
        return BaseResponse.successData(authenticateService.verifyAccount(request));
    }

    @PutMapping("regenerate-otp")
    public ResponseEntity<?> regenerateOTP(@Valid @RequestBody RegenerateOtpRequest request) {
        return BaseResponse.successData(authenticateService.regenerateOTP(request));
    }

    @PutMapping("forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return BaseResponse.successData(authenticateService.forgotPassword(request));
    }

    @PutMapping("profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return BaseResponse.successData(authenticateService.updateProfile(request));
    }

    @GetMapping("profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String token) {
        String jwt = token.substring(7);
        String email=jwtConfig.getUserIdFromJWT(jwt);
        UserDto userDto=userService.selectUserByEmail(email);
        return BaseResponse.successData(userDto);
    }
}
