# QA — PCFlow V1.3

Validação executada no GitHub Actions sobre a pasta isolada `PCFlow-v1.3.0/`.

## Windows

Comandos executados:

```powershell
dotnet restore PCFlow-v1.3.0/PCFlow.sln
dotnet build PCFlow-v1.3.0/PCFlow.sln -c Release --no-restore
dotnet publish PCFlow-v1.3.0/windows/PCFlow.Windows/PCFlow.Windows.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true
```

Resultado: **aprovado**. O projeto compilou em Release e o EXE x64 self-contained foi publicado com sucesso.

## Android

Comandos executados:

```bash
gradle -p PCFlow-v1.3.0/android :app:testDebugUnitTest --stacktrace
gradle -p PCFlow-v1.3.0/android :app:assembleDebug --stacktrace
```

Resultado: **aprovado**. Testes unitários configurados passaram e o APK foi montado com sucesso.

## Artefatos gerados

- `PCFlow-v1.3.0-Windows.exe`
- `PCFlow-v1.3.0-Android.apk`

## Limite desta validação

A compilação e os testes automatizados foram executados, mas esta execução não teve acesso físico ao computador Windows e ao Android do usuário ao mesmo tempo. Portanto, latência, gestos reais, comportamento do teclado Samsung/Gboard, troca de Wi-Fi e uma sessão longa PC ↔ Android ainda devem ser confirmados em hardware real. Build aprovado não é tratado como substituto para esse teste ponta a ponta.
