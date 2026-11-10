# Ledger de implementação — reconstrução do controle remoto

Relatório detalhado da execução de preflight. A reconstrução de features não começou.

## Relatório desta execução

- STATUS: PASS
- Base: `7af76ed6606b282dcce56de60fbed472ca699630` (`main`)
- Branch: `cursor/rebuild-preflight-df46` (criada a partir de `main`; working tree estava limpa; um único worktree em `/workspace`)
- Head: o commit que adiciona este arquivo. O PR de rascunho aponta esse commit.
- Escopo executado: seção 1, seção 15 e passos 1 a 4 da seção 20 do prompt mestre. Features não foram implementadas. O passo 5 da seção 20 (disparar agentes) ficou de fora de propósito.
- Arquivos desta mudança: `docs/engineering/implementation-ledger.md`, `docs/qa/video-feature-coverage.md`, `docs/superpowers/plans/2026-11-10-remote-control-rebuild.md`, `docs/reference/02_ANALISE_VIDEO_FRAME_A_FRAME.md`, `docs/reference/analysis_assets/` (frames; o mp4 ficou de fora).
- Risco: a gravação mostra Desligar, Repouso, Reiniciar e Encerrar sessão sem diálogo; o código atual também dispara `shutdown` sem confirmação. A task T39 continua obrigatória. Preços e marca do modal PRO não foram copiados.

## Repositório

| Item | Valor |
|------|--------|
| Raiz | `/workspace` |
| Remote | `origin` → `github.com/AnderHonorato/Conectar-PCFlow` |
| Branch de partida | `main`, alinhada a `origin/main` |
| Último commit da base | `7af76ed6606b282dcce56de60fbed472ca699630` — `ci: disponibilizar apenas APK oficial do Flow Tutum Next para entrega ao Drive` (2026-11-09) |
| Commit anterior | `d7cfbb4916022d4bc286d058a004d6ec4c1ee1c4` — `feat: cria núcleo funcional do PCFlow Windows + Android` |
| Worktree | uma, em `/workspace`, não havia worktree isolada antes desta branch |

## Estrutura e stacks

Não há projeto Web, protocolo compartilhado, backend de conta, testes, Gradle Wrapper nem módulo Android além de `:app`.

```text
PCFlow.sln
windows/PCFlow.Windows/          WinExe WPF + WinForms, net8.0-windows
  Core/                          modelos, TCP/UDP, SendInput, energia
  MainWindow.xaml                UI do host
android/                         Gradle Kotlin DSL, módulo :app
  app/src/main/java/com/ander/pcflow/
    MainActivity.kt              Compose: descoberta, touchpad, teclado, mídia, energia
    SessaoPcFlow.kt              UDP, TCP, PIN, token, reconexão em memória
    ServicoConexao.kt            foreground service
    Modelos.kt                   PcEncontrado, EstadoConexao
docs/ARCHITECTURE.md
docs/ROADMAP.md
.github/workflows/build.yml
.github/workflows/export-flow-tutum-next.yml
```

| Stack | O que o repo usa | O que não existe |
|-------|------------------|------------------|
| Android | Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, Material 3, Coroutines, `minSdk` 26, `compileSdk`/`targetSdk` 35, JVM target 17 | Navigation Compose, DataStore, Room, módulos por feature, testes, Wrapper |
| Windows | C# / .NET 8 (`net8.0-windows`), WPF, WinForms (`NotifyIcon`), `OutputType` WinExe, pacote `QRCoder` 1.6.0 sem uso | Projeto de teste, instalador, serviço, IPC |
| Web | — | PWA, TypeScript, Playwright |
| Protocolo | JSON por linha, campo `tipo`, portas UDP 45455 e TCP 45456 | Versão de sessão, id, ack, heartbeat, capabilities, schema compartilhado |
| Conta | — | Auth, perfil, entitlement |
| CI | `build.yml` compila Windows e APK debug; `export-flow-tutum-next.yml` baixa um APK de release | Lint, testes, contrato, Play, instalador |

`docs/ARCHITECTURE.md` descreve o núcleo LAN e sugere extrair o servidor para um processo ou serviço. Esse trecho fica superado pelo ruling ADR-004: input continua num agente por usuário, fora da Session 0.

`docs/ROADMAP.md` marca o núcleo v0.1 como feito, inclusive reconexão, e deixa clipboard, giroscópio, navegador, testes e criptografia em aberto. A ordem do roadmap antigo não é a ordem desta reconstrução. Streaming, gamepad, webcam e monitor virtual estão no roadmap e fora da seção 7; só entram se T00 mostrar essas funções no vídeo.

## Identidade, pacotes e artefatos

### Código deste repositório

| Campo | Valor |
|-------|--------|
| Nome de produto | PCFlow |
| `applicationId` / namespace | `com.ander.pcflow` |
| `versionName` / `versionCode` | `0.1.0` / `1` |
| Label | `PCFlow` (`AndroidManifest.xml` e `res/values/strings.xml`) |
| Assembly Windows | `PCFlow` (`PCFlow.Windows.csproj`) |
| Solution | `PCFlow.sln` |
| Projeto Gradle | `PCFlowAndroid` |
| Artefato de CI Android | `PCFlow-Android-debug` em `android/app/build/outputs/apk/debug/app-debug.apk` |
| Artefato de CI Windows | `PCFlow-Windows-x64`, publish `win-x64`, single-file, não self-contained |
| Formato | APK debug a partir do fonte; executável WinExe. Não há AAB nem instalador neste código |

Build local deste preflight, depois de instalar SDK: APK debug de 9 747 678 bytes, `applicationId` `com.ander.pcflow`, `versionName` `0.1.0`, `versionCode` `1`, label `PCFlow`.

### Release publicado que não nasce deste código

O workflow `.github/workflows/export-flow-tutum-next.yml` baixa o asset do release `flow-tutum-next-v2.0.0-alpha.10-as`. Inspeção só de metadados públicos e do manifesto:

| Campo | Valor |
|-------|--------|
| Nome do release | Flow Tutum Next 2.0.0 alpha.10 (Android AS) |
| Arquivo | `FlowTutum-Next-2.0.0-alpha.10-AS.apk` |
| Tamanho | 53 895 bytes |
| SHA-256 do arquivo | igual ao pin do workflow (`B98DB57D…9F99`) |
| Pacote | `br.flowtutum` |
| versionCode / versionName | 11 / `2.0.0-alpha.10-AS` |
| Activity | `br.flowtutum.MainActivity` |
| Evidência de UI | `classes.dex` referencia `android.webkit.WebView` e `JavascriptInterface` |
| VCS dentro do APK | `NO_SUPPORTED_VCS_FOUND` |
| Permissões | `INTERNET`, `ACCESS_NETWORK_STATE` |
| Consulta de pacote | `com.tailscale.ipn` |

Esse APK não preenche a matriz de cobertura. Endereços de LAN e hostname que aparecem nele e no texto do release não são reproduzidos aqui e não viram configuração do PCFlow.

## Signing

Nenhum segredo foi impresso. Não há `.jks`, `.keystore`, `local.properties`, `signingConfigs`, senha ou token de assinatura no código. `.gitignore` ignora `*.jks`, `*.keystore` e `local.properties`.

| Alvo | O que existe |
|------|----------------|
| Android deste repo | Sem config de release. O CI chama só `:app:assembleDebug`. O APK gerado neste ambiente saiu com o certificado padrão de debug (`CN=Android Debug`). O fingerprint desse certificado é da máquina de build e não foi copiado para o ledger. |
| Windows | Sem Authenticode e sem instalador. |
| APK Flow Tutum | Assinado por certificado autoassinado. Dados públicos do certificado: `CN=Flow Tutum`, `OU=Ander Flow`, `O=Ander Flow`, `C=BR`, válido de 2026-11-06 a 2054-03-23, SHA-256 `3E:04:9F:4A:03:6F:AE:0E:A1:5F:EF:FE:56:1C:47:B0:EE:26:30:0A:4F:E4:5A:E8:E6:B7:4A:94:59:9E:84:40`. A chave privada não está neste workspace. |

Release assinada do PCFlow continua bloqueada até existir uma keystore fora do git. Esta execução não gerou keystore.

## Baseline executado em 2026-11-10

Ambiente de partida: JDK 21.0.12 já instalado, sem `dotnet`, sem Gradle, sem Android SDK. JDK 17, pedido pelo README, não estava instalado. O build Android usou JDK 21 com `jvmTarget` 17. O CI continua em Java 17; esta diferença fica registrada.

Ferramentas instaladas só na máquina, fora do git: .NET SDK 8.0.427, Gradle 8.9, Android command-line tools, `platforms;android-35`, `build-tools;35.0.0`.

| Comando | Resultado |
|---------|-----------|
| `dotnet build PCFlow.sln -c Release` | FAIL, exit 1. `NETSDK1100`: para compilar `net8.0-windows` neste SO é preciso `EnableWindowsTargeting`. Falha preexistente de plataforma, não introduzida por esta branch. |
| `dotnet build PCFlow.sln -c Release -p:EnableWindowsTargeting=true` | PASS de compilação. 0 warnings, 0 errors. Saída: `windows/PCFlow.Windows/bin/Release/net8.0-windows/PCFlow.dll` e apphost `PCFlow`. Isso não executa a UI no Linux. |
| `dotnet test PCFlow.sln -c Release` | Exit 1 pelo mesmo `NETSDK1100`. Nenhum teste chegou a correr. |
| `dotnet test PCFlow.sln -c Release -p:EnableWindowsTargeting=true --list-tests` | Exit 0. A solution tem um único projeto, o WinExe. O alvo VSTest não listou nenhum teste. 0 testes. |
| `gradle -p android :app:assembleDebug` com Gradle 8.9 e SDK 35 | PASS. `BUILD SUCCESSFUL in 1m 25s`, 36 tasks. APK debug descrito acima. `validateSigningDebug` usou a chave de debug. |
| `gradle -p android :app:testDebugUnitTest --offline` | Exit 0 com `compileDebugUnitTestKotlin NO-SOURCE` e `testDebugUnitTest NO-SOURCE`. 0 testes. Não é suíte verde. |

Não há testes instrumentados, suíte Web, suíte de contrato nem execução física. O app Windows não foi iniciado.

## Achados de leitura no código atual

Preservar e cobrir com teste antes de trocar o desenho:

- Descoberta UDP `PCFLOW_DISCOVER_V1` e sessão TCP com PIN de 6 dígitos, token de 256 bits e comparação de token em tempo constante.
- Mouse relativo, clique esquerdo/direito/meio no Windows, scroll, texto Unicode, teclas especiais limitadas, mídia por virtual key, bandeja, minimizar ao fechar, pausa do servidor, foreground service.
- O agente já é WinExe, sem console.

Incompleto ou em conflito com o prompt, sem ser tratado como defeito introduzido agora:

- Token Android em `SharedPreferences`, chave `token_${pc.host}`. O IP é identidade. `allowBackup` está `true`.
- `protocolo` sai na descoberta e o cliente ignora o campo. Sem heartbeat e sem capabilities.
- Reconexão com backoff existe só em memória, no mesmo host, no mesmo processo.
- `detectTapGestures` e `detectDragGestures` estão empilhados no mesmo touchpad.
- Clique é down+up imediato. Não há drag, trilho de scroll, botão do meio na UI, nem soltura de segurança.
- Energia (`shutdown`, `restart`, `sleep`, `hibernate`, `signout` só no servidor) corre sem confirmação e sem política. Teste automatizado não pode chamar esses comandos de verdade.
- `stop` de mídia, `signout`, `middle` e Home/End/PgUp/PgDn existem no servidor e não na UI, total ou parcialmente.
- `IniciarServidorAutomaticamente` não registra inicialização. `QRCoder` não é usado.
- `MainWindow.xaml` deixa Dispositivos, Recursos e Ajustes desabilitados, com tooltip de próxima etapa.
- Vários clientes TCP podem enviar input ao mesmo tempo. O PIN é comparado sem tempo constante.
- `CHANGE_WIFI_STATE` está no manifesto sem uso encontrado. A permissão de notificação é pedida no launch. O ícone da notificação é o drawable de Bluetooth do sistema.
- Não há cifra de transporte. `docs/ARCHITECTURE.md` e o README já dizem isso.

## Rulings aceitos para o plano

Ainda não estão implementados. Detalhe e consequência ficam no plano.

| ID | Ruling |
|----|--------|
| ADR-001 | O contrato novo é JSON por linha, versionado, com schema único para Kotlin, C# e TypeScript. As mensagens v1 continuam num adaptador até os testes de contrato cobrirem a paridade. |
| ADR-002 | O hot path Android↔Windows permanece TCP até um benchmark justificar troca. O navegador entra por WebSocket no mesmo dispatcher de comandos. |
| ADR-003 | Bluetooth espera um spike Android + Windows antes de escolher RFCOMM ou BLE. O comando não conhece o transporte. |
| ADR-004 | O input fica no WinExe de bandeja, por usuário. Serviço do Windows, se um dia existir para update ou startup, só fala com o agente por IPC local autenticado. |
| ADR-005 | Produto e `applicationId` continuam PCFlow / `com.ander.pcflow`. O APK `br.flowtutum` não é base de código, de marca nem de URL. |
| ADR-006 | Conta e PRO fazem parte do produto e ficam fora do hot path. Sem login falso e sem preço copiado. O corte de implementação é o ADR-018. |
| ADR-007 | A identidade visual nova usa carvão e um verde próprio. O grafite/dourado/turquesa atual permanece até o slice de aparência. Sem cópia de arte de terceiros. |
| ADR-008 | Comando de energia só executa depois de confirmação na UI e de capability/política no agente. |
| ADR-009 | Identidade estável do dispositivo, não o IP. Segredo de sessão em armazenamento protegido (Keystore / DPAPI). |
| ADR-010 | O conjunto de comandos é fechado. Não há mensagem de shell arbitrário. |
| ADR-011 | Log não recebe tecla, clipboard, título de janela nem imagem transferida. |
| ADR-012 | Web nova em TypeScript, React e Vite, PWA mobile-first, porque não há stack Web no repo. |
| ADR-013 | Código existente sem teste permanece `PRESENT_UNVERIFIED` ou `PARTIAL`. `PASS` só com teste executado. |
| ADR-014 | A primeira mudança Android/Windows cria caracterização e uma costura testável. A UI deixa de conhecer socket aos poucos, com a suíte verde. |
| ADR-015 | Nenhuma keystore é gerada nem commitada nesta reconstrução sem decisão humana de release. |
| ADR-016 | Revisto com a gravação. Com a opção ligada, toque envia o modificador sozinho, segurar ativa a combinação e um segundo acionamento trava. A soltura em queda continua obrigatória. |
| ADR-017 | Streaming, gamepad, webcam, explorador de arquivos e monitor virtual ficam fora desta reconstrução, salvo se T00 encontrar a função no vídeo e uma task for adicionada antes de implementar. |
| ADR-018 | Decisão do dono em 2026-11-10, slice 12. Conta e PRO entram no app e nos contratos: auth, perfil, sync de ajustes e entitlement, atrás de portas e adaptadores. Este corte não liga banco de dados, backend real nem provedor de pagamento. Essas três ligações são dependência externa, à espera do dono, e ficam nomeadas no plano. Não são feature adiada nem botão falso. O ambiente dev usa adaptador em memória e entitlement `source=dev`, impossível em build de produção. O controle local do PC não chama essas portas. |

## Status dos slices

| Slice | Status |
|-------|--------|
| 0 Preflight, plano, matriz e leitura do vídeo | Feito nesta branch. Coluna de evidência preenchida. Implementação não começou. |
| 1 Sessão e descoberta | Implementado no corte pedido: protocolo 2, fixtures Kotlin e C#, descoberta UDP com id estável, lista, pareamento, revogação, máquina A.1, heartbeat, reconexão e safety release. T09 mDNS e T10 instância única/startup ficam de fora deste corte. |
| 2 a 11, 13 e 14 | Não iniciados. |
| 12 Conta e PRO | Plano revisto pelo ADR-018. Não implementado. Banco, backend e pagamento ficam fora do corte. |

## Leitura do vídeo

O corte de 3:34 foi lido pelos frames em `docs/reference/analysis_assets/` e registrado em `docs/reference/02_ANALISE_VIDEO_FRAME_A_FRAME.md`. A coluna de evidência da matriz deixou de estar pendente. Onde a função não aparece, a célula diz isso. ADR-016 mudou: toque envia o modificador, segurar ativa, segundo acionamento trava. O gyro mostrado é botão segurado mais inclinação. Energia aparece sem confirmação; T39 não foi afrouxada. Preços do modal não foram transcritos.

## Decisão do dono — slice 12

Em 2026-11-10 o dono do produto fechou o corte de conta e PRO. O registro normativo é o ADR-018. O plano em `docs/superpowers/plans/2026-11-10-remote-control-rebuild.md` deixa de pedir servidor de desenvolvimento, banco, backend real ou provedor de pagamento. O que o slice entrega, quando for implementado, são as portas `AccountPort`, `ProfilePort`, `SettingsSyncPort`, `EntitlementPort` e `BillingPort`, o contrato compartilhado e dois adaptadores: um de memória só em build debug ou internal, e um desconfigurado em produção. Entitlement com `source=dev` não pode ser aceito em release. Compra não conclui enquanto o dono não entregar o provedor. O socket de controle do PC abre com essas portas desligadas.

## Slice 1 executado

Branch `cursor/slice-1-session-discovery-df46`. O núcleo `windows/PCFlow.Core` fala o protocolo 2 (`session.hello`, `session.auth`, `session.capabilities`, `session.heartbeat`, `session.ack`, `session.error`). Descoberta UDP continua em `PCFLOW_DISCOVER_V1` e a resposta traz `deviceId`; sem `deviceId` o anúncio é ignorado. O token fica no id do PC, não no IP. Revogação marca o dispositivo e derruba a sessão. Queda com botão ou tecla presos gera soltura no agente. Três heartbeats perdidos saem de Ready. Build de produção de conta não entrou neste slice.

Verificação local: `dotnet test windows/PCFlow.Core.Tests/PCFlow.Core.Tests.csproj -c Release` — 23 passed, 0 failed. `gradle -p android :app:testDebugUnitTest` — 8 passed, 0 failed. `gradle -p android :app:assembleDebug` — sucesso. `dotnet build windows/PCFlow.Windows/PCFlow.Windows.csproj -c Release -p:EnableWindowsTargeting=true` — sucesso, 0 warnings.

## Fora desta execução

- Nenhuma feature, protocolo, CI ou assinatura foi alterada.
- Agentes A0–A14 não foram disparados. O plano nomeia o responsável de cada task para a próxima execução.
- `docs/release/remote-control-v2-verification.md` não existe e não deve ser escrito antes do critério da seção 18.
