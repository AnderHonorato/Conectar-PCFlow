$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $raiz

Write-Host '== PCFlow V1.3: Windows ==' -ForegroundColor Yellow
dotnet restore .\PCFlow.sln
dotnet build .\PCFlow.sln -c Release --no-restore
dotnet publish .\windows\PCFlow.Windows\PCFlow.Windows.csproj `
  -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
  -o .\release\windows

Write-Host '== PCFlow V1.3: Android ==' -ForegroundColor Yellow
gradle -p .\android :app:testDebugUnitTest --stacktrace
gradle -p .\android :app:assembleDebug --stacktrace

New-Item -ItemType Directory -Force -Path .\release | Out-Null
Copy-Item .\android\app\build\outputs\apk\debug\app-debug.apk .\release\PCFlow-v1.3-Android.apk -Force
Copy-Item .\release\windows\PCFlow.exe .\release\PCFlow-v1.3-Windows.exe -Force

Write-Host 'Build concluído. Artefatos em PCFlow-v1.3.0/release/' -ForegroundColor Green
