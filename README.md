# SMART AIR - Asthma Management Application

SMART AIR is a comprehensive Android application designed to help children, parents, and healthcare providers manage pediatric asthma effectively. The app provides tools for tracking medication usage, monitoring symptoms, analyzing triggers, and facilitating communication between families and healthcare providers.

---

## Table of Contents

1. [Overview](#overview)
2. [System Requirements](#system-requirements)
3. [Architecture](#architecture)
4. [User Roles](#user-roles)
5. [Features](#features)
   - [Authentication](#authentication)
   - [Child Dashboard](#child-dashboard)
   - [Parent Dashboard](#parent-dashboard)
   - [Provider Dashboard](#provider-dashboard)
   - [Medicine Logging](#medicine-logging)
   - [Symptom Check-In](#symptom-check-in)
   - [Peak Flow (PEF) Monitoring](#peak-flow-pef-monitoring)
   - [Emergency Triage](#emergency-triage)
   - [Medication Adherence Tracking](#medication-adherence-tracking)
   - [Inventory Management](#inventory-management)
   - [Trigger Pattern Analysis](#trigger-pattern-analysis)
   - [Statistics and Reports](#statistics-and-reports)
   - [Motivation System](#motivation-system)
   - [Data Sharing with Providers](#data-sharing-with-providers)
   - [Notifications](#notifications)
6. [Data Models](#data-models)
7. [Firebase Configuration](#firebase-configuration)
8. [Security Rules](#security-rules)
9. [Dependencies](#dependencies)
10. [Build Instructions](#build-instructions)

---

## Overview

SMART AIR enables families to:
- Track rescue inhaler and controller medicine usage
- Monitor daily symptoms and breathing quality
- Record Peak Expiratory Flow (PEF) readings with zone-based alerts
- Access emergency triage guidance during asthma attacks
- View adherence history and identify patterns
- Share selected health data with healthcare providers
- Stay motivated through streaks and achievement badges

---

## System Requirements

- Android SDK: Minimum 24 (Android 7.0 Nougat), Target 36
- Java Version: 11
- Firebase Project with Firestore, Authentication, and Cloud Messaging enabled

---

## Architecture

The application follows the **MVP (Model-View-Presenter)** architecture pattern for key modules such as login functionality.

### Directory Structure

```
app/src/main/java/com/example/b07project/
|-- adapters/          # RecyclerView adapters for lists
|-- auth/              # Authentication contracts and presenters (MVP)
|-- fragments/         # UI fragments for multi-step flows
|-- main/              # Entry activities (Welcome, Launcher)
|-- models/            # Data classes (POJOs for Firestore)
|-- repository/        # Data access layer (Firestore operations)
|-- services/          # Background services (Motivation tracking)
|-- utils/             # Utility classes (Reports, Notifications)
|-- *Activity.java     # Activity classes for each screen
```

### Key Design Patterns

- **MVP Architecture**: Used in the login module with `LoginContract`, `LoginPresenter`, and `LoginActivity`.
- **Repository Pattern**: All Firestore operations are abstracted into repository classes.
- **Callback Pattern**: Asynchronous operations use callback interfaces for success/failure handling.

---

## User Roles

### 1. Child
- Primary user of the daily tracking features
- Logs medication usage and symptoms
- Views their own dashboard with remaining doses and zone status
- Cannot access inventory management

### 2. Parent
- Manages child profiles and medication schedules
- Configures sharing settings with providers
- Tracks inventory and expiration dates
- Views detailed adherence calendars for each child
- Generates and shares invite codes for healthcare providers

### 3. Provider (Healthcare Professional)
- Links to patients via invite codes
- Views read-only dashboards with shared data only
- Accesses statistics, patterns, and history for linked patients
- Cannot modify patient data

---

## Features

### Authentication

**Files**: `LoginActivity.java`, `LoginPresenter.java`, `LoginContract.java`, `FirebaseAuthRepo.java`

- Email/password authentication via Firebase Auth
- Username-based login for child accounts (converted to email format internally)
- Email verification requirement for parent and provider accounts (bypassed for child accounts)
- Password reset functionality via email
- Role-based navigation after login:
  - Child: HomeActivity
  - Parent: DeviceChooserActivity
  - Provider: ProviderHomeActivity

### Child Dashboard

**Files**: `HomeActivity.java`, `activity_home.xml`

- Displays current asthma zone status (Green/Yellow/Red) based on latest PEF reading
- Shows remaining controller medicine doses for the day
- Quick access buttons for:
  - Logging medicine
  - Daily symptom check-in
  - Emergency triage
  - Peak flow entry
  - Viewing history logs
  - Inhaler technique guide
  - Streaks and badges
- Visual indicators showing which data categories are shared with providers

### Parent Dashboard

**Files**: `ParentDashboardActivity.java`, `ParentChildDashboardActivity.java`

- Lists all registered children with quick status overview
- Per-child dashboard with:
  - 30-day medication adherence calendar
  - Quick action buttons for logging on behalf of child
  - Access to all history logs
  - Schedule configuration
  - Sharing settings management
- Add new child functionality (new account or link existing)
- Invite code generation for providers

### Provider Dashboard

**Files**: `ProviderHomeActivity.java`, `ProviderChildInfoActivity.java`

- Lists all linked patients
- Per-patient view showing only data categories the parent has authorized
- Read-only access to:
  - Medication adherence calendar
  - Statistics and reports
  - Trigger patterns
  - Medicine, symptom, PEF, and incident history
- Add patient via invite code

### Medicine Logging

**Files**: `LogRescueInhalerActivity.java`, `ControllerMedicineRepository.java`, `RescueInhalerRepository.java`

- Log rescue inhaler or controller medicine usage
- Record:
  - Number of puffs/doses
  - Timestamp (automatically captured)
  - Triggers (exercise, cold air, pets, pollen, stress, smoke, weather, dust)
  - Post-dose status (better, same, worse)
  - Breathing rating (1-5 scale)
  - Optional notes
- Automatic inventory decrement when logging
- Alerts triggered for:
  - Reporting "worse" after rescue inhaler use
  - Rapid rescue usage (3+ puffs in 3 hours)

### Symptom Check-In

**Files**: `DailySymptomCheckInActivity.java`, `SymptomCheckInRepository.java`, `SymptomHistoryActivity.java`

- Daily symptom recording with:
  - Coughing frequency
  - Wheezing presence
  - Shortness of breath
  - Chest tightness
  - Night waking due to symptoms
  - Activity limitations
  - Overall feeling rating
- Historical view of all check-ins
- Data used for trigger pattern analysis

### Peak Flow (PEF) Monitoring

**Files**: `PEFEntryActivity.java`, `PEFHistoryActivity.java`, `PEFRepository.java`, `PersonalBest.java`

- Record PEF readings in L/min
- Automatic zone calculation based on personal best:
  - Green Zone: 80-100% of personal best (good control)
  - Yellow Zone: 50-79% of personal best (caution)
  - Red Zone: Below 50% of personal best (medical alert)
- Personal best management (set and update)
- Historical chart visualization using MPAndroidChart
- Zone change logging and alerts

### Emergency Triage

**Files**: `TriageActivity.java`, `TriageRepository.java`, `TriageSession.java`

- Guided decision support during breathing difficulties
- Assessment criteria:
  - Cannot speak in full sentences
  - Visible chest retractions
  - Blue lips or fingernails
  - Number of rescue inhaler attempts
  - Current PEF reading
- Decision outcomes:
  - Call 911 immediately (severe symptoms)
  - Wait and reassess with breathing pacer
  - Symptoms improving, continue monitoring
- 10-minute timer with animated breathing pacer
- Session logging for incident history
- Direct 911 call button

### Medication Adherence Tracking

**Files**: `ConfigureScheduleActivity.java`, `ScheduleRepository.java`, `MedicationSchedule.java`, `AdherenceAdapter.java`

- Configure daily medication schedule:
  - Select medicine from inventory
  - Set dosage per intake
  - Set frequency (1-4 times per day)
  - Set scheduled times for each dose
- 30-day adherence calendar visualization:
  - Green: All doses taken
  - Yellow: Partial doses taken
  - Red: No doses taken
  - Grey: Before schedule start date
- Real-time remaining doses counter on child dashboard

### Inventory Management

**Files**: `InventoryActivity.java`, `InventoryRepository.java`, `MedicineInventory.java`

- Track medicine inventory for parents:
  - Medicine name
  - Type (Rescue or Controller)
  - Total doses
  - Remaining doses
  - Purchase date
  - Expiration date
  - Assigned child (optional)
- Automatic dose decrement when logging
- Low inventory alerts (20% or less remaining)
- Expiration date warnings
- Add new medicine with all tracking fields

### Trigger Pattern Analysis

**Files**: `TriggerPatternsActivity.java`, `TriggerAnalyticsRepository.java`

- Analyzes logged triggers to identify patterns
- Displays:
  - Most common triggers based on frequency
  - Trigger correlation with rescue inhaler usage
  - Time-based patterns
- Visual charts for trigger distribution
- Recommendations based on identified patterns

### Statistics and Reports

**Files**: `StatisticsReportsActivity.java`, `StatisticsFragment.java`, `ReportsFragment.java`, `ReportGenerator.java`

- Statistics dashboard with:
  - Total rescue inhaler uses
  - Total controller medicine logs
  - Average PEF readings
  - Symptom trends
- Report generation:
  - Select date range
  - Choose data categories to include
  - Generate PDF or shareable summary
- Visual charts for trends over time

### Motivation System

**Files**: `MotivationActivity.java`, `MotivationService.java`, `Badge.java`, `Streak.java`

- Streak tracking:
  - Controller medicine streak (consecutive days)
  - Current and longest streak display
- Achievement badges:
  - First Rescue Log: Log your first rescue inhaler use
  - Perfect Controller Week: 7 consecutive days of controller adherence
  - Low Rescue Month: 4 or fewer rescue uses in 30 days
  - Technique Sessions: Complete 10 inhaler technique reviews
- Confetti animation when earning badges
- Badge earned notifications

### Data Sharing with Providers

**Files**: `SharingSettingsActivity.java`, `ParentCreateNewCodeActivity.java`, `ProviderUseInviteCodeActivity.java`

- Parent-controlled sharing settings per child:
  - Medication logs
  - Daily check-in/symptoms
  - Safety monitoring (PEF, incidents)
  - Trigger patterns
  - Statistics and reports
- Invite code system:
  - Parent generates time-limited code
  - Provider enters code to link patient
  - Code expires after set duration
  - Single-use codes
- Visual indicators on child dashboard showing shared categories

### Notifications

**Files**: `NotificationCenterActivity.java`, `NotificationHelper.java`, `NotificationRepository.java`, `AppNotification.java`

- In-app notification center
- Alert types:
  - Low inventory warnings
  - Medicine expiration alerts
  - Missed dose reminders
  - Worse-after-dose alerts (sent to parent)
  - Rapid rescue usage alerts (sent to parent)
  - Zone change alerts
- Firebase Cloud Messaging integration for push notifications

---

## Data Models

### MedicineLog (Base Class)
- userId: String
- timestamp: Date
- doseCount: int
- triggers: List of String
- notes: String
- enteredBy: String (Child/Parent)
- postDoseStatus: String (Better/Same/Worse)
- breathRating: int

### ControllerMedicineLog (extends MedicineLog)
- scheduledTime: Date
- takenOnTime: boolean

### RescueInhalerLog (extends MedicineLog)
- (inherits all base fields)

### MedicationSchedule
- userId: String
- medicationName: String
- dosagePerIntake: int
- frequency: int
- scheduledTimes: List of String
- startDate: Date

### MedicineInventory
- userId: String
- name: String
- type: String (Rescue/Controller)
- childId: String
- childName: String
- totalDoses: int
- remainingDoses: int
- expiryDate: Date
- purchaseDate: Date

### PEFReading
- userId: String
- value: int (L/min)
- timestamp: Date
- zone: String (green/yellow/red)
- percentageOfPB: int

### SymptomCheckIn
- userId: String
- timestamp: Date
- coughing: int
- wheezing: boolean
- shortnessOfBreath: int
- chestTightness: int
- nightWaking: boolean
- activityLimitation: int
- overallFeeling: int

### Badge
- userId: String
- type: String
- name: String
- description: String
- earned: boolean
- earnedDate: long
- progress: int
- requirement: int

### Streak
- userId: String
- type: String
- currentCount: int
- longestCount: int
- lastUpdated: long

### TriageSession
- userId: String
- timestamp: Date
- severity: String
- symptoms: List of String
- pefReading: int
- rescueAttempts: int
- outcome: String
- duration: long

### AppNotification
- userId: String
- title: String
- message: String
- type: String
- timestamp: Date
- read: boolean

---

## Firebase Configuration

### Firestore Collections

- `users`: User profiles with role and childIds array
- `children`: Child profiles with parentId and sharingSettings
- `controller_medicine_logs`: Controller medicine usage logs
- `rescue_inhaler_logs`: Rescue inhaler usage logs
- `medication_schedules`: Configured medication schedules (keyed by childId)
- `pef_readings`: Peak flow readings
- `pef_personal_bests`: Personal best PEF values
- `symptom_check_ins`: Daily symptom check-in records
- `medicine_inventory`: Medicine inventory items
- `streaks`: User streak data
- `badges`: User badge data
- `triage_sessions`: Emergency triage session records
- `notifications`: In-app notifications
- `invite_codes`: Provider invite codes

---

## Security Rules

The following Firestore security rules enforce role-based access:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // --- Helper Functions ---

    // Check if user is authenticated
    function isAuthenticated() {
      return request.auth != null;
    }
    
    // Check if user owns the document
    function isOwner(userId) {
      return request.auth.uid == userId;
    }

    // Check if the user is the parent of the child (by looking up the child doc)
    function isParentOfChild(childId) {
      let child = get(/databases/$(database)/documents/children/$(childId));
      return child != null && child.data.parentId == request.auth.uid;
    }

    // Check if the user is a provider for the child (by looking up the provider's list)
    function isProviderForChild(childId) {
      let userDoc = get(/databases/$(database)/documents/users/$(request.auth.uid));
      // Checks if the childId exists in the provider's 'childIds' array
      return userDoc != null && (childId in userDoc.data.childIds);
    }
    
        // Add this new rule for the schedule
    match /medication_schedules/{childId} {
      allow read, write: if request.auth != null;
    }

    // --- Collection Rules ---

    // Users (Parents, Providers, Children)
    match /users/{userId} {
      // Allow anyone to read users (needed for provider lookup), but only owner can write
      allow read: if isAuthenticated();           
      allow write: if isAuthenticated() && isOwner(userId);
    }

    // Children Profiles
    match /children/{childId} {
  	// Read: Anyone authenticated can read (needed for invite code redemption)
  	allow read: if isAuthenticated();

  	// Create: Parent only
  	allow create: if isAuthenticated() && request.auth.uid == request.resource.data.parentId;

  	// Update/Delete: Parent only
  	allow update, delete: if isAuthenticated() && resource.data.parentId == request.auth.uid;
	}	
    // --- Medical Data Collections ---
    // Updated to allow Parents and Providers to READ, but only Owners/Parents to WRITE

    // Rescue Inhaler Logs
    match /rescue_inhaler_logs/{logId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Controller Medicine Logs
    match /controller_medicine_logs/{logId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Symptom Check-ins
    match /symptom_checkins/{checkinId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // PEF Readings
    match /pef_readings/{readingId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Personal Bests
    match /personal_bests/{pbId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Zone Change Logs
    match /zone_change_logs/{logId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Triage Sessions
    match /triage_sessions/{sessionId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Streaks 
    match /streaks/{streakId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Badges
    match /badges/{badgeId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Controller Logs (Duplicate of controller_medicine_logs? Keeping just in case)
    match /controller_logs/{logId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }
    
    // Reports
    match /reports/{reportId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }

    // Inventory
    match /inventory/{itemId} {
      allow read: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId) || 
        isProviderForChild(resource.data.userId)
      );
      allow create: if isAuthenticated() && (
        isOwner(request.resource.data.userId) || 
        isParentOfChild(request.resource.data.userId)
      );
      allow update, delete: if isAuthenticated() && (
        isOwner(resource.data.userId) || 
        isParentOfChild(resource.data.userId)
      );
    }

    // Notifications
    match /notifications/{notificationId} {
      allow read, update, delete: if isAuthenticated() && isOwner(resource.data.userId);
      allow create: if isAuthenticated();
    }
        // Invite Codes
    match /invite_codes/{code} {
      // Anyone authenticated can read to validate a code
      allow read: if request.auth != null;
      // Only the creator (parent) or the consumer (provider) can delete
      allow create: if request.auth != null; // Parent creates
      allow delete: if request.auth != null; // Provider deletes after use
    }
  }
}
```

---

## Dependencies

### Firebase
- Firebase BOM (Bill of Materials)
- Firebase Authentication
- Firebase Firestore
- Firebase Cloud Messaging

### UI Libraries
- Material Design Components 1.12.0
- Konfetti (confetti animations) 2.0.4
- MPAndroidChart v3.1.0 (charts and graphs)
- Android YouTube Player 13.0.0 (inhaler technique videos)
- TapTargetView 1.13.3 (onboarding highlights)

### Utilities
- AndroidX Work Runtime 2.9.0 (background tasks)
- Google Guava 31.1-android (collections and utilities)
- OkHttp 4.12.0 (HTTP client)

### Testing
- JUnit
- Mockito 5.5.0
- Espresso
