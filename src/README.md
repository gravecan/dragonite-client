# HUD Config (Fabric 1.21)

Simple HUD/config helper mod for Minecraft Fabric 1.21.

## Build

**First time only** (download Gradle wrapper JAR):

```powershell
cd "c:\Users\drakpro6679\Downloads\Client that bypasses polish"
.\setup-gradle-wrapper.ps1
```

**Build the mod** (PowerShell):

```powershell
cd "c:\Users\drakpro6679\Downloads\Client that bypasses polish\Polish"
.\gradlew.bat clean build
```

Output JAR: `Polish\build\libs\cloth-config-15.0.140.jar`

## Usage

- **Right Control** – Open/close the config GUI (fixed, cannot be changed in settings).

## Features

- Simple dark-panel GUI with category tabs.
- Runtime-only string decoding (no plain literals in the built JAR).

Install Fabric Loader and Fabric API for 1.21, then drop the built JAR into `.minecraft/mods`.
