# PCFlow

PCFlow é um controle remoto local para Windows + Android, criado do zero para funcionar sem conta, sem anúncios e sem depender de nuvem.

> Estado atual: **v0.1.0-alpha**. A base funcional de conexão LAN, pareamento, touchpad, teclado, mídia, energia e execução em segundo plano já está implementada. Recursos avançados do roadmap ainda não devem ser tratados como concluídos.

## O que já funciona

### Windows
- servidor TCP local na porta `45456`;
- descoberta automática por broadcast UDP na porta `45455`;
- pareamento por PIN de 6 dígitos;
- token persistente por dispositivo autorizado;
- mouse remoto: movimento, clique esquerdo/direito/meio e scroll;
- entrada de texto Unicode e teclas especiais;
- controles de mídia do Windows;
- bloquear, suspender, hibernar, reiniciar, desligar e desligar monitor;
- interface WPF escura inspirada na referência visual do projeto;
- lista de dispositivos conhecidos;
- fechar a janela minimiza para a bandeja;
- servidor continua funcionando enquanto o app está na bandeja.

### Android
- descoberta automática de PCs na LAN;
- primeiro pareamento por PIN;
- reutilização do token salvo nas próximas conexões;
- tela de touchpad otimizada para toque;
- teclado remoto;
- controles de mídia;
- controles de energia;
- serviço em primeiro plano para reduzir encerramentos da conexão em segundo plano;
- interface nativa Kotlin + Jetpack Compose.

## Visual

A identidade usa grafite/preto, dourado quente e turquesa para estado conectado, com superfícies grandes, bordas discretas e navegação simples. A referência visual enviada serviu apenas como direção de design; os componentes foram recriados no projeto.

## Estrutura

```text
PCFlow.sln
windows/PCFlow.Windows/    Aplicativo/servidor Windows
android/                   Aplicativo Android
.github/workflows/         Builds automáticos
docs/                      Arquitetura e roadmap
```

## Desenvolvimento Windows

Requisitos:
- Windows 10/11;
- .NET 8 SDK.

```powershell
dotnet restore PCFlow.sln
dotnet build PCFlow.sln -c Release
dotnet run --project windows/PCFlow.Windows/PCFlow.Windows.csproj
```

## Desenvolvimento Android

Requisitos:
- Android Studio ou JDK 17 + Gradle 8.9;
- Android SDK 35.

Abra a pasta `android/` no Android Studio ou execute:

```bash
gradle -p android :app:assembleDebug
```

APK esperado:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Como conectar

1. Abra PCFlow no Windows.
2. Abra PCFlow no Android na mesma rede Wi-Fi/LAN.
3. Toque em **Atualizar** caso o PC ainda não tenha aparecido.
4. Selecione o computador.
5. No primeiro acesso, digite no celular o PIN exibido na lateral do programa Windows.
6. Depois do pareamento, o celular guarda o token do PC e não pede o PIN novamente.

## Segurança atual

A v0.1.0 restringe o fluxo de uso à rede local e exige pareamento/token para comandos. O protocolo ainda é TCP simples; **criptografia de transporte autenticada e pinagem de identidade estão no roadmap antes de uma versão estável 1.0**.

Não exponha as portas do PCFlow diretamente para a internet nesta versão.

## Próximas etapas

- reconexão automática completa após troca/quedas de Wi-Fi;
- confirmação configurável para comandos destrutivos;
- QR Code real para pareamento;
- clipboard bidirecional;
- Remote Desktop com streaming de baixa latência;
- transferência/explorador de arquivos;
- gamepad e editor de layouts;
- giroscópio/acelerômetro;
- apresentação e navegador;
- câmera virtual/projetor;
- segundo monitor virtual;
- painel de recursos e ajustes completo;
- instalador Windows e APK Release assinado;
- criptografia autenticada de ponta a ponta na LAN.

Veja [`docs/ROADMAP.md`](docs/ROADMAP.md).
