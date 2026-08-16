# Thai Language Localization & Language Switcher

This plan adds support for the Thai language and an in-app language switcher to PennyWise AI. Currently, the app's UI is mostly hard-coded in English, which prevents it from showing Thai even when the device region is set to Thailand.

## User Review Required

> [!IMPORTANT]
> **Translation Tone**: Following `docs/store-localization.md`, the Thai translation will use a **conversational** tone rather than a formal/literary one. Words like "SMS", "AI", "Bank", "Budget", "Scan" will be kept in English or as common Thai loanwords.
>
> **Brand Identity**: "PennyWise AI" and "Pro" will remain in Latin script.

## Proposed Changes

### 1. Data Layer (Preferences)
Store the user's language choice in DataStore.

#### [MODIFY] [UserPreferencesRepository.kt](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/preferences/UserPreferencesRepository.kt)
- Add `APP_LANGUAGE` key.
- Add `language: Flow<String?>` to `UserPreferences`.
- Add `updateLanguage(languageCode: String?)` method.

---

### 2. UI Layer (Infrastructure & Resources)
Extract hard-coded strings and provide Thai translations.

#### [MODIFY] [strings.xml (en)](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/res/values/strings.xml)
- Extract labels for: Navigation (Home, Analytics, Chat), Settings sections, common buttons (Cancel, OK, Save), and feature descriptions.

#### [NEW] [strings.xml (th)](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/res/values-th/strings.xml)
- Provide conversational Thai translations for all extracted strings.

---

### 3. Presentation Layer (Settings & Main)
Add the UI for language selection.

#### [MODIFY] [SettingsViewModel.kt](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsViewModel.kt)
- Expose `language` state from repository.
- Method to update language using `AppCompatDelegate.setApplicationLocales`.

#### [MODIFY] [SettingsScreen.kt](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsScreen.kt)
- Add "Language" row under the **Personalization** section.
- Use `SettingsNavItem` and an `AlertDialog` with radio buttons for selection.

#### [MODIFY] [BottomNavItem.kt](file:///Users/ginkless/Desktop/pennywiseai-tracker-full/pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/presentation/navigation/BottomNavItem.kt)
- Use `stringResource()` for titles.

---

## Verification Plan

### Automated Tests
- `./init.sh app` to ensure no build regressions.

### Manual Verification
1. Open **Settings > Personalization**.
2. Select **Language**.
3. Choose **ไทย (Thai)**.
4. Verify all UI labels change to Thai immediately.
5. Verify the tone is conversational as per requirements.
6. Verify "PennyWise AI" remains in English.
7. Restart the app and verify the language choice is persisted.
