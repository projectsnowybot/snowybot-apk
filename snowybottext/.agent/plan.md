owybot/# Project Plan

Create an Android app based on the Python script snowybot.py.

Overview:
snowybot.py is an automated stateful dice betting bot for just-dice.com implementing the 'Peanut Strategy' wager progression algorithm. The Android app replicates and expands upon this text-based/automation script into a robust Kotlin Jetpack Compose application.

Core Requirements & Features:
1. Strategy Engine: Port the Peanut Strategy logic (unit stake calculations, safety checkpoints, profit and drawdown jumps, wobble factor adjustment, state serialization) into pure Kotlin with Coroutines and StateFlow.
2. Web Automation & Automation Engine: Integrate headless WebView / JavaScript bridge / API to interact with just-dice.com (authentication, 2FA input, reading balance, wins/losses, roll history, placing wagers `#a_lo` at 49.5% target chance).
3. Foreground Service & Watchdog: Android Foreground Service with persistent status notification for long-running background execution, auto-reconnect, stall watchdog timer, and auto-stop target balance limit (e.g. 144 target balance).
4. Jetpack Compose UI & Dashboard:
   - Credentials & Login screen (Username, Password, 2FA prompt/secret, Proxy options).
   - Real-time Metrics Dashboard (Wallet Stash, Total Profit/Loss, Current Wager Amount, Safety Checkpoint, Session Wins/Losses, Target Goal progress).
   - Strategy Settings & Configuration (Starting balance, Base peanut size, multiplier rules).
   - Live Logs & Wager History Console (streamed roll logs, status updates, outcome history).
5. State Persistence & Analytics: Encrypted SharedPreferences for credentials, DataStore/Room for bot state and roll history persistence across restarts.

Design: Modern Material 3 dark-themed financial/bot dashboard UI with clear controls (Play/Pause/Stop), status indicators, and clean text/console tabs.

## Project Brief

# Project Brief: SnowyBot Android (MVP)

## Features

1. **Peanut Strategy Engine & Web Bridge**: Pure Kotlin strategy engine porting the Peanut Strategy wager algorithm (unit stake calculations, safety checkpoints, drawdown jumps, wobble factor adjustment) operating over a headless WebView / JavaScript bridge to interact with just-dice.com.
2. **Foreground Execution & Watchdog Service**: Android Foreground Service with a persistent status notification for continuous long-running execution, auto-reconnect, stall watchdog timer, and auto-stop target balance limiters.
3. **Real-Time Financial Dashboard**: Jetpack Compose Material 3 dark-themed dashboard displaying live wallet metrics, total profit/loss, current wager, safety checkpoints, target goal progress, and main control buttons (Play, Pause, Stop).
4. **Live Console & Wager History**: Streamed activity log interface displaying real-time roll outcomes, system status updates, and wager history logs.
5. **Credentials & Strategy Configuration**: Configuration screens for user authentication (Username, Password, 2FA secret) and customizable strategy parameters (base unit size, target limit, multiplier rules).

## High-Level Tech Stack

* **Language & Concurrency**: Kotlin, Kotlin Coroutines, StateFlow / Flow
* **UI Framework**: Jetpack Compose, Material 3 (Dark Theme)
* **Navigation & Adaptive Strategy**: **Jetpack Navigation 3** (state-driven navigation) and **Compose Material Adaptive** library
* **Background Execution**: Android Foreground Service with persistent status notification
* **Web Integration**: Android WebView & JavaScript Interface bridge
* **Persistence & Security**: EncryptedSharedPreferences (credentials) and Jetpack DataStore / Room (bot state & roll history)

## Implementation Steps
**Total Duration:** 1h 32m 44s

### Task_1_EngineAndWebBridge: Implement Peanut Strategy Kotlin engine, EncryptedSharedPreferences for credentials/config, DataStore/Room for roll history, and WebView JS bridge for just-dice.com integration.
- **Status:** COMPLETED
- **Updates:** Implemented PeanutStrategyState, PeanutEngine (with 5 passing unit tests), CredentialRepository (EncryptedSharedPreferences), Room AppDatabase & RollDao, BotStateRepository (DataStore), JustDiceJsBridge, and JustDiceWebBridge. App assembled debug and passed unit tests successfully.
- **Acceptance Criteria:**
  - Peanut Strategy wager algorithm, safety checkpoints, drawdown jumps, wobble factor ported to pure Kotlin
  - EncryptedSharedPreferences and DataStore/Room setup for credentials and bot state
  - WebView JavaScript bridge configured to interact with just-dice.com and stream roll results
  - build pass
- **Duration:** 7m 7s

### Task_2_ForegroundService: Implement Android Foreground Service with persistent status notification, watchdog timer, auto-reconnect, and balance limiter logic to run bot in background.
- **Status:** COMPLETED
- **Updates:** Implemented JustDiceBotService foreground service with dataSync foreground service type, persistent notification showing live balance/profit/status with interactive actions, 30s stall watchdog auto-reconnect, target limit auto-stop (default 144), Room roll saving, DataStore state auto-saving, updated AndroidManifest.xml, and added passing service unit tests. `./gradlew assembleDebug testDebugUnitTest` passed.
- **Acceptance Criteria:**
  - Foreground Service running with persistent notification displaying live balance/status
  - Watchdog timer detecting stall and handling auto-reconnect or auto-stop limits
  - build pass
- **Duration:** 3m 13s

### Task_3_UI_DashboardConsoleAndConfig: Build Jetpack Compose Material 3 dark-themed UI with real-time financial dashboard, live console logs with wager history, and strategy configuration screens.
- **Status:** COMPLETED
- **Updates:** Implemented Jetpack Compose Material 3 dark-themed financial UI with DashboardScreen (HeroWalletCard, StatusHeaderCard, TargetProgressCard, ServiceControlsBar), ConsoleScreen (live log terminal & Room Wager History list), ConfigScreen (EncryptedSharedPreferences credentials, target balance, proxy settings), MainAppScreen with NavigationBar/NavigationRail adaptive navigation, and Jetpack Compose Previews. App assembled debug and passed all 13 unit tests.
- **Acceptance Criteria:**
  - Dashboard screen displaying wallet metrics, total profit/loss, wager status, and Play/Pause/Stop controls
  - Live console log tab displaying streamed activity and roll outcomes
  - Configuration screen for user credentials and customizable strategy parameters
  - build pass
- **Duration:** 6m 8s

### Task_4_RunAndVerify: Run and verify application stability, ensure no crashes during betting loops/background service, confirm alignment with user requirements, and report critical UI or runtime issues.
- **Status:** COMPLETED
- **Updates:** Verified application stability, screen navigation, Peanut Strategy execution, AI model dropdown selection, "Reload" single-line button formatting, and "Reset State File" button on both Dashboard and Configuration screens with confirmation dialog. App passed all 17 unit tests, built cleanly, and passed critic_agent quality checks with 0 crashes.
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - critic_agent verifies app stability and requirement alignment
- **Duration:** 1h 16m 16s

