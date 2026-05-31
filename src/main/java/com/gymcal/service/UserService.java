package com.gymcal.service;

import com.gymcal.dto.AuthDTOs;
import com.gymcal.dto.FoodDTOs;
import com.gymcal.model.User;
import com.gymcal.repository.UserRepository;
import com.gymcal.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j @Service @RequiredArgsConstructor
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private NutritionCalculatorService calculator;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    public AuthDTOs.AuthResponse register(AuthDTOs.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new IllegalArgumentException("Email already registered");

        double bmi = calculator.calculateBMI(request.getWeightKg(), request.getHeightCm());
        double bmr = calculator.calculateBMR(request.getWeightKg(), request.getHeightCm(), request.getAge(), request.getGender());
        double tdee = calculator.calculateTDEE(bmr, request.getActivityLevel());
        int dailyCal = calculator.calculateDailyCalorieTarget(tdee, request.getGoal());
        double protein = calculator.calculateProteinTarget(request.getWeightKg(), request.getGoal());
        double fat  = calculator.calculateFatTarget(dailyCal, request.getGoal());
        double carbs = calculator.calculateCarbTarget(dailyCal, protein, fat);

        User user = User.builder()
                .name(request.getName()).email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .weightKg(request.getWeightKg()).heightCm(request.getHeightCm())
                .age(request.getAge()).gender(request.getGender().toUpperCase())
                .bmi(bmi).bmiCategory(calculator.getBMICategory(bmi))
                .goal(request.getGoal().toUpperCase()).activityLevel(request.getActivityLevel().toUpperCase())
                .dailyCalorieTarget(dailyCal).dailyProteinTarget(protein).dailyCarbTarget(carbs).dailyFatTarget(fat)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(jwtService.generateToken(saved.getId(), saved.getEmail()), saved);
    }

    public AuthDTOs.AuthResponse login(AuthDTOs.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new BadCredentialsException("Invalid email or password");
        return buildAuthResponse(jwtService.generateToken(user.getId(), user.getEmail()), user);
    }

    public FoodDTOs.UserProfileResponse getProfile(String userId) {
        return mapToProfile(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found")));
    }

    public FoodDTOs.UserProfileResponse updateGoal(String userId, FoodDTOs.UpdateGoalRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (request.getGoal()          != null) user.setGoal(request.getGoal().toUpperCase());
        if (request.getActivityLevel() != null) user.setActivityLevel(request.getActivityLevel().toUpperCase());
        if (request.getWeightKg()      != null) user.setWeightKg(request.getWeightKg());
        if (request.getHeightCm()      != null) user.setHeightCm(request.getHeightCm());
        if (request.getAge()           != null) user.setAge(request.getAge());
        if (request.getHealthConditions() != null) user.setHealthConditions(request.getHealthConditions());

        double bmi = calculator.calculateBMI(user.getWeightKg(), user.getHeightCm());
        user.setBmi(bmi); user.setBmiCategory(calculator.getBMICategory(bmi));
        double bmr = calculator.calculateBMR(user.getWeightKg(), user.getHeightCm(), user.getAge(), user.getGender());
        double tdee = calculator.calculateTDEE(bmr, user.getActivityLevel());
        int dailyCal = calculator.calculateDailyCalorieTarget(tdee, user.getGoal());
        double protein = calculator.calculateProteinTarget(user.getWeightKg(), user.getGoal());
        double fat = calculator.calculateFatTarget(dailyCal, user.getGoal());
        double carbs = calculator.calculateCarbTarget(dailyCal, protein, fat);
        user.setDailyCalorieTarget(dailyCal); user.setDailyProteinTarget(protein);
        user.setDailyCarbTarget(carbs); user.setDailyFatTarget(fat);
        user.setUpdatedAt(LocalDateTime.now());
        return mapToProfile(userRepository.save(user));
    }

    private AuthDTOs.AuthResponse buildAuthResponse(String token, User user) {
        return AuthDTOs.AuthResponse.builder()
                .token(token).userId(user.getId()).name(user.getName()).email(user.getEmail())
                .bmi(user.getBmi()).bmiCategory(user.getBmiCategory()).goal(user.getGoal())
                .dailyCalorieTarget(user.getDailyCalorieTarget()).dailyProteinTarget(user.getDailyProteinTarget())
                .dailyCarbTarget(user.getDailyCarbTarget()).dailyFatTarget(user.getDailyFatTarget())
                .build();
    }

    private FoodDTOs.UserProfileResponse mapToProfile(User user) {
        return FoodDTOs.UserProfileResponse.builder()
                .id(user.getId()).name(user.getName()).email(user.getEmail())
                .weightKg(user.getWeightKg()).heightCm(user.getHeightCm())
                .age(user.getAge()).gender(user.getGender())
                .bmi(user.getBmi()).bmiCategory(user.getBmiCategory())
                .goal(user.getGoal()).activityLevel(user.getActivityLevel())
                .dailyCalorieTarget(user.getDailyCalorieTarget()).dailyProteinTarget(user.getDailyProteinTarget())
                .dailyCarbTarget(user.getDailyCarbTarget()).dailyFatTarget(user.getDailyFatTarget())
                .healthConditions(user.getHealthConditions())
                .build();
    }
}
