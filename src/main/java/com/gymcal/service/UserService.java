package com.gymcal.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gymcal.dto.AuthDTOs;
import com.gymcal.dto.FoodDTOs;
import com.gymcal.model.User;
import com.gymcal.repository.UserRepository;
import com.gymcal.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service	
@RequiredArgsConstructor
public class UserService {

	@Autowired
    private  UserRepository userRepository;
	@Autowired
    private  NutritionCalculatorService calculator;
	@Autowired
    private  JwtService jwtService;
	@Autowired
    private  PasswordEncoder passwordEncoder;
    

    public AuthDTOs.AuthResponse register(AuthDTOs.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Calculate BMI
        double bmi = calculator.calculateBMI(request.getWeightKg(), request.getHeightCm());
        String bmiCategory = calculator.getBMICategory(bmi);

        // Calculate nutrition targets
        double bmr = calculator.calculateBMR(request.getWeightKg(), request.getHeightCm(),
                request.getAge(), request.getGender());
        double tdee = calculator.calculateTDEE(bmr, request.getActivityLevel());
        int dailyCalories = calculator.calculateDailyCalorieTarget(tdee, request.getGoal());
        double protein = calculator.calculateProteinTarget(request.getWeightKg(), request.getGoal());
        double fat = calculator.calculateFatTarget(dailyCalories, request.getGoal());
        double carbs = calculator.calculateCarbTarget(dailyCalories, protein, fat);

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .weightKg(request.getWeightKg())
                .heightCm(request.getHeightCm())
                .age(request.getAge())
                .gender(request.getGender().toUpperCase())
                .bmi(bmi)
                .bmiCategory(bmiCategory)
                .goal(request.getGoal().toUpperCase())
                .activityLevel(request.getActivityLevel().toUpperCase())
                .dailyCalorieTarget(dailyCalories)
                .dailyProteinTarget(protein)
                .dailyCarbTarget(carbs)
                .dailyFatTarget(fat)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail());

        return buildAuthResponse(token, saved);
    }

    public AuthDTOs.AuthResponse login(AuthDTOs.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return buildAuthResponse(token, user);
    }

    public FoodDTOs.UserProfileResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToProfile(user);
    }

    public FoodDTOs.UserProfileResponse updateGoal(String userId, FoodDTOs.UpdateGoalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update fields if provided
        if (request.getGoal() != null) user.setGoal(request.getGoal().toUpperCase());
        if (request.getActivityLevel() != null) user.setActivityLevel(request.getActivityLevel().toUpperCase());
        if (request.getWeightKg() != null) user.setWeightKg(request.getWeightKg());
        if (request.getHeightCm() != null) user.setHeightCm(request.getHeightCm());
        if (request.getAge() != null) user.setAge(request.getAge());

        // Recalculate BMI
        double bmi = calculator.calculateBMI(user.getWeightKg(), user.getHeightCm());
        user.setBmi(bmi);
        user.setBmiCategory(calculator.getBMICategory(bmi));

        // Recalculate targets
        double bmr = calculator.calculateBMR(user.getWeightKg(), user.getHeightCm(), user.getAge(), user.getGender());
        double tdee = calculator.calculateTDEE(bmr, user.getActivityLevel());
        int dailyCalories = calculator.calculateDailyCalorieTarget(tdee, user.getGoal());
        double protein = calculator.calculateProteinTarget(user.getWeightKg(), user.getGoal());
        double fat = calculator.calculateFatTarget(dailyCalories, user.getGoal());
        double carbs = calculator.calculateCarbTarget(dailyCalories, protein, fat);

        user.setDailyCalorieTarget(dailyCalories);
        user.setDailyProteinTarget(protein);
        user.setDailyCarbTarget(carbs);
        user.setDailyFatTarget(fat);
        user.setUpdatedAt(LocalDateTime.now());

        User updated = userRepository.save(user);
        return mapToProfile(updated);
    }

    private AuthDTOs.AuthResponse buildAuthResponse(String token, User user) {
        return AuthDTOs.AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bmi(user.getBmi())
                .bmiCategory(user.getBmiCategory())
                .goal(user.getGoal())
                .dailyCalorieTarget(user.getDailyCalorieTarget())
                .dailyProteinTarget(user.getDailyProteinTarget())
                .dailyCarbTarget(user.getDailyCarbTarget())
                .dailyFatTarget(user.getDailyFatTarget())
                .build();
    }

    private FoodDTOs.UserProfileResponse mapToProfile(User user) {
        return FoodDTOs.UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .weightKg(user.getWeightKg())
                .heightCm(user.getHeightCm())
                .age(user.getAge())
                .gender(user.getGender())
                .bmi(user.getBmi())
                .bmiCategory(user.getBmiCategory())
                .goal(user.getGoal())
                .activityLevel(user.getActivityLevel())
                .dailyCalorieTarget(user.getDailyCalorieTarget())
                .dailyProteinTarget(user.getDailyProteinTarget())
                .dailyCarbTarget(user.getDailyCarbTarget())
                .dailyFatTarget(user.getDailyFatTarget())
                .build();
    }
}
