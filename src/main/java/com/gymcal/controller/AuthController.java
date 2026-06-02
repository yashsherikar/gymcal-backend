package com.gymcal.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gymcal.dto.AuthDTOs;
import com.gymcal.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	@Autowired
	private  UserService userService;
	


	@PostMapping("/register")
	public ResponseEntity<?> register(@Valid @RequestBody AuthDTOs.RegisterRequest request) {
		try {
			AuthDTOs.AuthResponse response = userService.register(request);
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody AuthDTOs.LoginRequest request) {
		try {
			AuthDTOs.AuthResponse response = userService.login(request);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
		}
	}

	@GetMapping("/health")
	public ResponseEntity<?> health() {
		return ResponseEntity.ok(Map.of("status", "UP", "service", "GymCal API"));
	}
}
