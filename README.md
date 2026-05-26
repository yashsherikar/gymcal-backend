# GymCal Backend 🏋️
> Spring Boot + MongoDB + Anthropic AI — Gym Calorie & Nutrition Management API

---

## 🚀 Quick Start (Local)

### Prerequisites
- Java 17+
- Maven 3.8+
- MongoDB (local) or MongoDB Atlas (free cloud)
- Anthropic API key

### Run locally
```bash
# Clone and enter project
cd gymcal-backend

# Set environment variables
export MONGODB_URI="mongodb://localhost:27017/gymcal"
export ANTHROPIC_API_KEY="sk-ant-your-key-here"
export JWT_SECRET="your-secret-key-minimum-32-characters"

# Run
mvn spring-boot:run
```

Server starts at: `http://localhost:8080`

---

## 🗄️ MongoDB Atlas Setup (Free)

1. Go to https://cloud.mongodb.com → Create free account
2. Create **Free Cluster (M0)**
3. Add database user → remember username & password
4. Network Access → Add IP `0.0.0.0/0` (allow all for now)
5. Connect → Drivers → Copy connection string
6. Replace `<password>` in the URI with your actual password
7. Your URI: `mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/gymcal`

---

## ☁️ Deploy FREE on Render

1. Push this project to **GitHub**
2. Go to https://render.com → New → Web Service
3. Connect your GitHub repo
4. Settings:
   - **Runtime**: Docker
   - **Plan**: Free
5. Add Environment Variables:
   - `MONGODB_URI` → your Atlas URI
   - `ANTHROPIC_API_KEY` → your Anthropic key
   - `JWT_SECRET` → any random 32+ char string
   - `CORS_ORIGINS` → your frontend URL (e.g., `https://yourapp.vercel.app`)
6. Deploy!

> ⚠️ Free tier spins down after inactivity. First request after sleep takes ~30s.

---

## 📡 API Reference

### Base URL
```
Local:      http://localhost:8080/api
Production: https://gymcal-backend.onrender.com/api
```

### Auth Headers (for protected routes)
```
Authorization: Bearer <your_jwt_token>
```

---

### 🔐 Authentication

#### POST /auth/register
Register with BMI + Goal setup.
```json
{
  "name": "Rahul Sharma",
  "email": "rahul@example.com",
  "password": "password123",
  "weightKg": 75,
  "heightCm": 175,
  "age": 25,
  "gender": "MALE",
  "goal": "WEIGHT_LOSS",
  "activityLevel": "MODERATE"
}
```
Goals: `WEIGHT_LOSS` | `MUSCLE_GAIN` | `MAINTAIN` | `RECOMPOSITION`
Activity: `SEDENTARY` | `LIGHT` | `MODERATE` | `ACTIVE` | `VERY_ACTIVE`

**Response:**
```json
{
  "token": "eyJhbGci...",
  "userId": "64abc123",
  "name": "Rahul Sharma",
  "bmi": 24.5,
  "bmiCategory": "Normal Weight",
  "goal": "WEIGHT_LOSS",
  "dailyCalorieTarget": 1760,
  "dailyProteinTarget": 165.0,
  "dailyCarbTarget": 176.0,
  "dailyFatTarget": 48.9
}
```

#### POST /auth/login
```json
{ "email": "rahul@example.com", "password": "password123" }
```

---

### 👤 User

#### GET /user/profile  🔒
Returns full profile with BMI and macro targets.

#### PUT /user/goal  🔒
Update goal, weight, activity level. Targets recalculated automatically.
```json
{
  "goal": "MUSCLE_GAIN",
  "activityLevel": "ACTIVE",
  "weightKg": 73.5
}
```

---

### 🍎 Food

#### POST /food/search  🔒
Search food nutrition via AI (preview — does NOT add to log).
```json
{ "foodName": "chicken breast", "quantityGrams": 200 }
```
**Response:**
```json
{
  "foodName": "Chicken Breast (cooked)",
  "quantityGrams": 200,
  "calories": 330,
  "proteinGrams": 62.0,
  "carbsGrams": 0.0,
  "fatGrams": 7.2,
  "fiberGrams": 0.0,
  "aiAnalysis": "Lean protein source, excellent for muscle building",
  "success": true
}
```

#### POST /food/log  🔒
Add food to daily log. Pass nutrition from search result to avoid double AI call.
```json
{
  "foodName": "Chicken Breast (cooked)",
  "quantityGrams": 200,
  "mealType": "LUNCH",
  "logDate": "2025-06-01",
  "calories": 330,
  "proteinGrams": 62.0,
  "carbsGrams": 0.0,
  "fatGrams": 7.2,
  "fiberGrams": 0.0,
  "aiAnalysis": "Lean protein source..."
}
```
MealType: `BREAKFAST` | `LUNCH` | `DINNER` | `SNACK`

#### GET /food/daily?date=2025-06-01  🔒
Get today's full summary (omit date for today).

**Response:**
```json
{
  "date": "2025-06-01",
  "targetCalories": 1760,
  "targetProtein": 165.0,
  "consumedCalories": 850.0,
  "consumedProtein": 82.5,
  "remainingCalories": 910.0,
  "calorieProgress": 48.3,
  "proteinProgress": 50.0,
  "meals": [
    {
      "mealType": "BREAKFAST",
      "totalCalories": 350,
      "items": [...]
    }
  ]
}
```

#### GET /food/weekly  🔒
Get last 7 days summary array.

#### DELETE /food/log/{logId}  🔒
Delete a specific food log entry.

---

## 🧮 BMI & Nutrition Logic

| BMI Range | Category |
|-----------|----------|
| < 18.5 | Underweight |
| 18.5–24.9 | Normal Weight |
| 25–29.9 | Overweight |
| ≥ 30 | Obese |

| Goal | Calorie Adjustment | Protein |
|------|-------------------|---------|
| Weight Loss | -20% TDEE | 2.2g/kg |
| Muscle Gain | +10% TDEE | 2.5g/kg |
| Maintain | TDEE | 1.8g/kg |
| Recomposition | TDEE | 2.3g/kg |

Formula: **Mifflin-St Jeor BMR** → TDEE × Activity Multiplier

---

## 📁 Project Structure
```
src/main/java/com/gymcal/
├── GymCalApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   └── FoodController.java
├── dto/
│   ├── AuthDTOs.java
│   └── FoodDTOs.java
├── model/
│   ├── User.java
│   └── FoodLog.java
├── repository/
│   ├── UserRepository.java
│   └── FoodLogRepository.java
├── security/
│   ├── JwtService.java
│   └── JwtAuthFilter.java
└── service/
    ├── UserService.java
    ├── FoodLogService.java
    ├── NutritionCalculatorService.java
    └── AnthropicService.java
```
