# AutismServerBridge

Fabric 1.20.1 mod for the MintServers world. Build it with Java 17.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat build
```

The jar is `build\libs\AutismServerBridge-1.0.0.jar`. Put it in the server `mods` folder and in the CurseForge client `mods` folder.
