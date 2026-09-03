# Compilar do zero

## O que você precisa

| | Versão | Para |
|---|---|---|
| .NET SDK | 8.0 | Windows e núcleo |
| JDK | 17 | Android |
| Android SDK | API 35 + build-tools 35.0.0 | APK |
| Gradle | 8.9+ | Android |

## Windows

```bash
dotnet restore PCFlow.sln
dotnet build PCFlow.sln -c Release
dotnet test windows/PCFlow.Core.Tests/PCFlow.Core.Tests.csproj

# executável único (precisa do .NET 8 Desktop Runtime na máquina)
dotnet publish windows/PCFlow.Windows/PCFlow.Windows.csproj -c Release -r win-x64 \
  --self-contained false -p:PublishSingleFile=true -o release/windows

# portátil, não precisa de runtime instalado (~150 MB)
dotnet publish windows/PCFlow.Windows/PCFlow.Windows.csproj -c Release -r win-x64 \
  --self-contained true -p:PublishSingleFile=true -o release/windows-portatil
```

### Compilar em Linux ou macOS

O projeto declara `EnableWindowsTargeting`, então a interface WPF **compila**
fora do Windows (só não executa). É assim que a CI valida a interface antes de
gastar um runner Windows:

```bash
dotnet build PCFlow.sln -c Release -p:EnableWindowsTargeting=true
```

## Android

```bash
echo "sdk.dir=/caminho/do/android-sdk" > android/local.properties
cd android
gradle :app:testDebugUnitTest
gradle :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Assinatura do APK

O `build.gradle.kts` lê `android/keystore.properties`, que **não é versionado**.
Sem ele, o release é assinado com a chave de depuração — instala normalmente,
mas não serve para atualizar um app já instalado com outra chave.

Para gerar a sua:

```bash
cd android
keytool -genkeypair -v -keystore pcflow-release.jks -alias pcflow \
  -keyalg RSA -keysize 4096 -validity 10950

cat > keystore.properties <<'FIM'
storeFile=pcflow-release.jks
storePassword=SUA_SENHA
keyAlias=pcflow
keyPassword=SUA_SENHA
FIM
```

> **Guarde o `.jks` e a senha.** Perdeu a chave, perdeu a capacidade de atualizar
> o app: o Android recusa instalar por cima uma versão assinada com chave
> diferente, obrigando a desinstalar e perder os pareamentos.

## Build limpo

```bash
dotnet clean PCFlow.sln
cd android && gradle clean
```

## CI

`.github/workflows/build.yml` roda em três etapas:

1. **Ubuntu** — compila tudo (WPF incluso) e roda os 75 testes .NET.
2. **Windows** — recompila nativamente, roda os testes de novo e publica
   `PCFlow.exe`, o `.zip` portátil e os SHA-256.
3. **Ubuntu** — testes Kotlin, lint e os dois APKs com SHA-256.

Os artefatos ficam disponíveis para download na página da execução.
