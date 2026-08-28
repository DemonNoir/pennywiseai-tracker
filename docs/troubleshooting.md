# Troubleshooting Guide

This document records known build issues and their resolutions for agents and developers.

## Build Issues

### `jlink` or `jmod` executable does not exist
**Error:**
```
Execution failed for task ':app:compileStandardDebugJavaWithJavac'.
> Could not resolve all files for configuration ':app:androidJdkImage'.
   > Failed to transform core-for-system-modules.jar ...
      > Execution failed for JdkImageTransform: ...
         > jlink executable /path/to/jre/bin/jlink does not exist.
```

**Cause:**
The IDE (e.g., VS Code or Android Studio with certain extensions) is using a stripped-down JRE that lacks modular development tools like `jlink` and `jmod`. This often happens with the Red Hat Java extension's embedded JRE on macOS/Linux.

**Resolution:**
Symbolic link the missing tools from a full JDK (like Eclipse Temurin or JetBrains Runtime) into the bin directory of the JRE being used by the IDE.

**Steps taken for `ginkless` environment:**
1.  Identify the full JDK: `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/`
2.  Identify the IDE's JRE bin: `/Users/ginkless/.antigravity-ide/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64/bin/`
3.  Create symlinks:
    ```bash
    ln -s /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jlink /Users/ginkless/.antigravity-ide/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64/bin/jlink
    ln -s /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jmod /Users/ginkless/.antigravity-ide/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64/bin/jmod
    ```

**Alternative Fix:**
Configure the IDE's Gradle settings to use a full JDK path instead of the default/embedded JRE.
In Android Studio: `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK`.

### Function invocation 'context(...)' expected in `SettingsScreen.kt`
**Error:**
```
e: file:///.../SettingsScreen.kt:253:40 Function invocation 'context(...)' expected.
```

**Cause:**
The `context` variable (from `LocalContext.current`) was referenced in a `Toast.makeText` call inside a dialog's `onClick` handler before its declaration line. Because `context` is a reserved keyword for Kotlin Context Receivers, the compiler misidentified it as a context receiver invocation. Additionally, `action_confirm` and `action_cancel` string resources were missing.

**Resolution:**
1.  Moved `val context = LocalContext.current` to the top of the `SettingsScreen` Composable to ensure it's in scope for all handlers.
2.  Updated the missing string resource references to use the existing `common_confirm` and `common_cancel`.
