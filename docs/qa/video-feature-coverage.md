# Matriz de cobertura das funções da seção 7

Base de leitura: `7af76ed6606b282dcce56de60fbed472ca699630` (`main`).
Data da leitura: 2026-11-10.
Esta matriz descreve o que o código deste repositório implementa. Ela não declara a reconstrução pronta.

## Evidência de vídeo

Coluna preenchida em 2026-11-10 a partir de `docs/reference/analysis_assets/` e de `docs/reference/02_ANALISE_VIDEO_FRAME_A_FRAME.md`. A gravação dura 3:34. `não aparece neste corte` significa que a função não foi mostrada; não é permissão para apagá-la do plano quando o prompt mestre ainda a exige. Marca, ícones e preços da gravação não são evidência de implementação.

## Status

| Status | Significado |
|--------|-------------|
| `PRESENT_UNVERIFIED` | O comportamento básico está no fonte citado. Não há teste automatizado. Isto não é `PASS`. |
| `PARTIAL` | Há código relacionado e o requisito da seção 7 continua incompleto. |
| `ABSENT` | Não há implementação desse requisito. |
| `N/A` | A coluna da plataforma não se aplica. Usado dentro das colunas Android, Windows ou Web. |

Nenhum `PASS` foi atribuído. O repositório não tem projeto de teste, e o Apêndice J proíbe `PASS` sem teste ou artefato verificável.

## Contagem

- Linhas da seção 7: 122
- `PRESENT_UNVERIFIED`: 13
- `PARTIAL`: 19
- `ABSENT`: 90

## Matriz

| ID | Evidência vídeo | Feature | Android | Windows | Web | Testes | Status |
|----|-----------------|---------|---------|---------|-----|--------|--------|
| V01 | não aparece neste corte | Lista de PCs | `android/app/src/main/java/com/ander/pcflow/MainActivity.kt` (`TelaConectar`) | `windows/PCFlow.Windows/Core/ServidorPcFlow.cs` (resposta UDP) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V02 | 01:24–03:32 cabeçalho com PC e janela; Wi-Fi ou Bluetooth só na ajuda 00:14–00:16 | Status e método de conexão | `MainActivity.kt` (texto Conectado e host:porta; sem método de transporte) | `windows/PCFlow.Windows/MainWindow.xaml` (`TextoStatus`) | ABSENT | nenhum | PARTIAL |
| V03 | ajuda 00:14–00:16; sessão já conectada 01:24–03:32, sem descoberta | Wi-Fi / LAN | `android/app/src/main/java/com/ander/pcflow/SessaoPcFlow.kt` (UDP 45455, TCP 45456) | `ServidorPcFlow.cs` (mesmas portas) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V04 | só o texto da ajuda 00:14–00:16; sem sessão Bluetooth | Bluetooth | ABSENT (o ícone da notificação é drawable de sistema, sem stack Bluetooth) | ABSENT | ABSENT | nenhum | ABSENT |
| V05 | ajuste ligado 00:04–00:06; queda não aparece | Reconexão automática | `SessaoPcFlow.kt` (`agendarReconexao`, só em memória e no mesmo host) | Nova conexão TCP é aceita; sem heartbeat | ABSENT | nenhum | PARTIAL |
| V06 | subtítulo do ajuste 00:04–00:06; lista do último PC não aparece | Último PC | `SessaoPcFlow.kt` (`ultimoPc` volátil) | `windows/PCFlow.Windows/Core/Modelos.cs` (`DispositivoAutorizado`, por id) | ABSENT | nenhum | PARTIAL |
| V07 | não aparece neste corte | Erros de permissão, protocolo e rede | `SessaoPcFlow.kt` (mensagem genérica de exceção; sem fluxo de rede local) | `ServidorPcFlow.cs` (PIN inválido, dispositivo não autorizado) | ABSENT | nenhum | PARTIAL |
| V08 | não aparece neste corte | Heartbeat | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V09 | não aparece neste corte | Negociação de capabilities | `SessaoPcFlow.kt` ignora `protocolo` | `ServidorPcFlow.cs` envia `protocolo = 1` só na descoberta | ABSENT | nenhum | ABSENT |
| V10 | 01:48 e 02:04–02:42, área dominante | Touchpad em área grande | `MainActivity.kt` (`Touchpad`) | N/A | ABSENT | nenhum | PARTIAL |
| V11 | ajuda 00:18; ponto do dedo 02:06–02:20 | Pointer relativo | `MainActivity.kt` (`mouse_move` com delta) | `windows/PCFlow.Windows/Core/EntradaWindows.cs` (`Mover`, `MOUSEEVENTF_MOVE`) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V12 | ajuda 00:18, toque igual a clique | Toque = clique esquerdo | `MainActivity.kt` (`detectTapGestures`) | `EntradaWindows.cs` (`Clique` left) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V13 | três zonas sem rótulo 01:48; ajuste 00:00 | Botão direito e do meio | `MainActivity.kt` (só direito na UI) | `EntradaWindows.cs` (right e middle) | ABSENT | nenhum | PARTIAL |
| V14 | não aparece neste corte | Press e release separados | ABSENT (o clique envia down+up juntos) | `EntradaWindows.cs` (`Clique` sempre down+up) | ABSENT | nenhum | ABSENT |
| V15 | não aparece neste corte | Arrastar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V16 | trilho com setas 01:48–03:32; gesto de dois dedos não aparece | Scroll por gesto e trilho lateral | `MainActivity.kt` (botões de scroll, sem gesto nem trilho) | `EntradaWindows.cs` (`Scroll`) | ABSENT | nenhum | PARTIAL |
| V17 | slider 00:00–00:02 | Sensibilidade | `MainActivity.kt` (fator fixo 1.35) | N/A | ABSENT | nenhum | PARTIAL |
| V18 | não aparece neste corte; linha Avançado 00:04 não abre | Velocidade de scroll | `MainActivity.kt` (delta fixo 120) | N/A | ABSENT | nenhum | PARTIAL |
| V19 | não aparece neste corte | Scroll natural / invertido | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V20 | ajuste ligado 00:04; ponto no touchpad 02:06–02:20 | Feedback visual opcional do pointer | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V21 | switch 00:00; zonas visíveis 01:48 | Mostrar ou ocultar botões do mouse | ABSENT (botões sempre visíveis) | N/A | ABSENT | nenhum | ABSENT |
| V22 | switch ligado 00:00; layout não muda no touchpad mostrado | Modo uma mão | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V23 | switch desligado 00:00; espelhamento não demonstrado | Espelhamento para canhoto | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V24 | switch 00:00; botão circular 01:48–03:32 | Botão de Gyro Mouse | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V25 | não observável neste corte | Coalescing de pointer.move sem descartar click/key | ABSENT (`enviar` não agrega nem prioriza) | `windows/PCFlow.Windows/Core/ExecutorComandos.cs` executa na hora | ABSENT | nenhum | ABSENT |
| V26 | não há inclinação; toast 01:52 manda segurar o botão e girar | Sensor nativo do giroscópio | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V27 | não aparece neste corte | Calibração | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V28 | não aparece neste corte | Zona morta | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V29 | não aparece neste corte | Filtro | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V30 | não aparece neste corte | Ganho | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V31 | não aparece neste corte | Pausa em background ou desconexão | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V32 | não aparece neste corte | Teste de drift | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V33 | toast 01:52; botão visível 01:48 | Estado háptico e visual | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V34 | radial 01:48; ajuda 00:20–00:26 | Play / pause | `MainActivity.kt` (`playpause`) | `ExecutorComandos.cs` (VK 0xB3) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V35 | anterior e próximo no radial 01:48 | Faixa anterior e próxima | `MainActivity.kt` (`previous`, `next`) | `ExecutorComandos.cs` (VK 0xB1, 0xB0) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V36 | botão quadrado 01:48 | Stop | ABSENT na UI | `ExecutorComandos.cs` aceita `stop` (VK 0xB2) | ABSENT | nenhum | PARTIAL |
| V37 | mais e menos no radial 01:48 | Volume mais e menos | `MainActivity.kt` (`volumedown`, `volumeup`; também envia `mute`) | `ExecutorComandos.cs` (VK 0xAE, 0xAF, 0xAD) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V38 | não aparece neste corte | Estado e feedback de mídia | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V39 | ajuda 00:20, adapta à janela ativa | Integração com sessão de mídia do sistema, sem clique em pixel | N/A | `ExecutorComandos.cs` (virtual keys, sem SMTC) | ABSENT | nenhum | PARTIAL |
| V40 | dock 01:50–02:42 | Enumerar janelas e aplicativos | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V41 | ícones 01:50–02:28; título no cabeçalho 01:48 em diante | Ícone e título | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V42 | ponto sob ícone 02:26–02:28 | Indicador de em execução | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V43 | título muda 01:48, 01:52, 02:10, 02:14, 02:18, 02:22 | Título da janela ativa no cabeçalho | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V44 | ajuda 01:00; toque de ativação não isolado | Toque ativa ou abre | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V45 | 02:28 Exibir, Zoom, Ocultar, Sair; ajuda 01:00 | Toque longo: Exibir, Zoom, Ocultar, Sair | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V46 | ajuda de apps 01:00 cita tela cheia; sem ação ao vivo | Tela cheia quando o sistema expõe essa ação | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V47 | item Sair 02:28; sem prova de fechamento gracioso | Fechamento gracioso | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V48 | 02:48 | Navegador: voltar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V49 | 02:48 | Navegador: avançar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V50 | 02:48 | Navegador: nova aba ou janela | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V51 | 02:48 | Navegador: fechar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V52 | 02:48 | Navegador: zoom mais e menos | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V53 | 02:48 | Navegador: recarregar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V54 | 02:48 | Navegador: início | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V55 | dois ícones sem rótulo em 02:48; ajuda 00:28 | Comando adicional de navegador ou janela, conforme o desenho final | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V56 | chips 02:48; ajuda 00:30–00:42 adicionar e excluir | Favoritos: criar, ler, atualizar e apagar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V57 | texto da ajuda 00:28–00:42; sync ao vivo não aparece | Sincronização opcional de favoritos pela conta | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V58 | ajuda 00:28 cita vários navegadores; sem teste no corte | Testes de compatibilidade de navegador | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V59 | IME do sistema 03:02–03:04 | IME nativo (Gboard ou teclado do sistema) | `MainActivity.kt` (`OutlinedTextField`; não é modo direto) | N/A | ABSENT | nenhum | PARTIAL |
| V60 | não aparece neste corte | Texto Unicode | `MainActivity.kt` (`texto`) | `EntradaWindows.cs` (`Texto`, `KEYEVENTF_UNICODE`) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V61 | 03:04–03:06 | Modo direto de teclado | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V62 | ajuste desligado 00:04; modo não demonstrado | Modo buffer | `MainActivity.kt` (`TecladoRemoto`, envio pelo botão) | `EntradaWindows.cs` (`Texto`) | ABSENT | nenhum | PARTIAL |
| V63 | Tab e enter no painel 03:06 | Enter, backspace e tab | `MainActivity.kt` | `ExecutorComandos.cs` (VK 0x0D, 0x08, 0x09) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V64 | IME abre 03:02–03:04; fechar não isolado | Foco e abrir ou fechar teclado | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V65 | 03:06 não desenha A–Z; letras ficam no IME 03:02 | Sem teclado alfabético desenhado à mão | `MainActivity.kt` (não há teclado alfabético desenhado) | N/A | N/A | nenhum | PRESENT_UNVERIFIED |
| V66 | Shift, Ctrl, Win e Alt 03:12–03:32 | Shift, Ctrl, Windows e Alt | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V67 | subtítulo 00:04; ajuda 01:06 | Máquina de estados toque versus segurar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V68 | não aparece neste corte | Soltura garantida de modificador e tecla | ABSENT | `EntradaWindows.cs` (`Tecla` faz down+up imediato; sem safety release) | ABSENT | nenhum | ABSENT |
| V69 | 03:06 e ajuda 01:06 | F1 a F12 | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V70 | Esc e Tab 03:06 | Esc e Tab | `MainActivity.kt` | `ExecutorComandos.cs` (VK 0x1B, 0x09) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V71 | Insert, Home, PgUp, Delete, End, PgDn 03:06 | Insert, Home, PgUp, Delete, End, PgDn | `MainActivity.kt` (só DELETE) | `ExecutorComandos.cs` (DELETE, HOME, END, PAGEUP, PAGEDOWN; sem INSERT) | ABSENT | nenhum | PARTIAL |
| V72 | setas 03:06 | Setas | `MainActivity.kt` | `ExecutorComandos.cs` (VK 0x25–0x28) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V73 | bloco numérico, operadores e enter 03:06 | Teclado numérico, operadores e Enter numérico | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V74 | 03:12 | Desligar | `MainActivity.kt` (`shutdown`) | `ExecutorComandos.cs` (`shutdown /s /t 0`, sem confirmação) | ABSENT | nenhum | PARTIAL |
| V75 | 03:12 | Repouso | `MainActivity.kt` (`sleep`) | `ExecutorComandos.cs` (`rundll32` SetSuspendState) | ABSENT | nenhum | PARTIAL |
| V76 | 03:12 | Reiniciar | `MainActivity.kt` (`restart`) | `ExecutorComandos.cs` (`shutdown /r /t 0`) | ABSENT | nenhum | PARTIAL |
| V77 | 03:12 | Encerrar sessão | ABSENT na UI | `ExecutorComandos.cs` aceita `signout` (`shutdown /l`) | ABSENT | nenhum | PARTIAL |
| V78 | não aparece; 03:12 mostra os quatro botões sem diálogo | Confirmação de energia | `MainActivity.kt` (aviso textual, sem diálogo) | ABSENT | ABSENT | nenhum | ABSENT |
| V79 | não aparece neste corte | Política e capability no servidor | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V80 | não aparece neste corte | Testes seguros de energia | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V81 | ícones de texto e de imagem 03:14–03:32 | Clipboard Android para Windows: texto e imagem | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V82 | 03:14–03:32; ajuda 01:12 | Clipboard Windows para Android: texto e imagem | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V83 | não aparece neste corte | Hash antieco | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V84 | não aparece neste corte | Checagem de MIME e tamanho | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V85 | não aparece neste corte | Privacidade e redação de log | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V86 | não aparece; a gravação é só o cliente Android | Estados de permissão de clipboard na Web | N/A | N/A | ABSENT | nenhum | ABSENT |
| V87 | linha 00:00; fluxo não aberto | Conta / login | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V88 | linha 00:00; modal 01:43, 02:52 e 03:24 | PRO / detalhes | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V89 | linha 00:00; tela não aberta | Personalizar painéis | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V90 | switch 00:00 | Botões do mouse | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V91 | switch 00:00 | Gyro Mouse | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V92 | switch 00:00 | Uma mão | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V93 | switch 00:00 | Canhoto | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V94 | slider 00:00–00:02 | Velocidade do pointer | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V95 | não aparece neste corte | Velocidade do scroll | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V96 | não aparece neste corte | Scroll natural | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V97 | não aparece; a trava vista em 01:06 é de modificador | Trava de arraste e de botão | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V98 | switch 00:04 | Feedback de entrada | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V99 | switch desligado 00:04 | Buffer de teclado | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V100 | switch ligado 00:04; ajuda 01:06 | Modificador ao tocar | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V101 | switch desligado 00:04 | Botões de volume do aparelho | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V102 | switch ligado 00:04 | Sons de toque | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V103 | switch ligado 00:04–00:06 | Reconexão automática (ajuste) | `SessaoPcFlow.kt` (ligada no código, sem ajuste) | ABSENT | ABSENT | nenhum | PARTIAL |
| V104 | switch ligado 00:04–00:06 | Manter a tela acesa | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V105 | switch desligado 00:04–00:06 | Trava de retrato | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V106 | padrão do sistema 00:06 | Tema do sistema | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V107 | grade 00:06–00:08 | Papéis de parede embutidos | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V108 | linha 00:06; seletor do sistema 01:33–01:37 | Papel de parede por foto | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V109 | valor Português 00:06; seletor não abre | Idioma | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V110 | pager 00:10–00:16 | Primeira conexão paginada | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V111 | 00:18 | Ajuda de gestos básicos | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V112 | 00:20–01:16 | Ajuda de mídia, navegador, apps, atalhos e clipboard | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V113 | lista 00:52–01:00 | Rotas de suporte, FAQ e contato | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V114 | navegador do aparelho 01:18–01:24 | Links externos seguros | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V115 | estrutura 00:08–01:16; o texto da gravação não será copiado | Conteúdo de ajuda próprio | ABSENT | N/A | ABSENT | nenhum | ABSENT |
| V116 | linha 00:00; fluxo não aberto | Autenticação real de conta | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V117 | não aparece neste corte | Perfil | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V118 | subtítulo da conta 00:00; sync não acontece no corte | Sincronização de ajustes | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V119 | modal 01:43, 02:52 e 03:24 | Entitlement | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V120 | controle segue após fechar o modal 01:46–01:48 e 03:26 | Controle local independente de nuvem | `SessaoPcFlow.kt` e `README.md` (somente LAN) | `ServidorPcFlow.cs` (somente local) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| V121 | modal mostra três períodos; valores não transcritos | Preços próprios e configuráveis | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |
| V122 | modal sobre a sessão já conectada 01:43, 02:52 e 03:24 | Feature gates testados no cliente e no companion | ABSENT | ABSENT | ABSENT | nenhum | ABSENT |

## Comportamentos que já existem e a seção 7 não lista

Estes itens não entram na contagem da seção 7. Ficam registrados para a reconstrução preservar o que funciona e corrigir o que está incompleto.

| ID | Evidência vídeo | Feature | Android | Windows | Web | Testes | Status |
|----|-----------------|---------|---------|---------|-----|--------|--------|
| E01 | não aparece neste corte | Pareamento por PIN de 6 dígitos e token | `SessaoPcFlow.kt` (`ola`, PIN, token) | `ServidorPcFlow.cs` | ABSENT | nenhum | PRESENT_UNVERIFIED |
| E02 | não aparece neste corte | Token do Android guardado por endereço IP | `SessaoPcFlow.kt` (`token_${pc.host}`) | Token associado ao id do dispositivo em `DispositivoAutorizado` | ABSENT | nenhum | PARTIAL |
| E03 | não aparece neste corte | Foreground service durante a sessão | `android/app/src/main/java/com/ander/pcflow/ServicoConexao.kt` | N/A | ABSENT | nenhum | PRESENT_UNVERIFIED |
| E04 | não aparece neste corte | Ícone de bandeja | N/A | `windows/PCFlow.Windows/MainWindow.xaml.cs` (`NotifyIcon`) | N/A | nenhum | PRESENT_UNVERIFIED |
| E05 | não aparece neste corte | Fechar a janela minimiza para a bandeja | N/A | `MainWindow.xaml` (`CheckMinimizar`) e `MainWindow.xaml.cs` | N/A | nenhum | PRESENT_UNVERIFIED |
| E06 | não aparece neste corte | Pausar o servidor | N/A | `ServidorPcFlow.cs` (`AlternarPausa`) | N/A | nenhum | PRESENT_UNVERIFIED |
| E07 | não aparece neste corte | Bloquear a estação | `MainActivity.kt` (`lock`) | `ExecutorComandos.cs` (`LockWorkStation`) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| E08 | não aparece neste corte | Hibernar | `MainActivity.kt` (`hibernate`) | `ExecutorComandos.cs` (`shutdown /h`, sem confirmação) | ABSENT | nenhum | PARTIAL |
| E09 | não aparece neste corte | Desligar o monitor | `MainActivity.kt` (`monitoroff`) | `ExecutorComandos.cs` (`SC_MONITORPOWER`) | ABSENT | nenhum | PRESENT_UNVERIFIED |
| E10 | não aparece neste corte | Dependência QRCoder sem uso no código | N/A | `windows/PCFlow.Windows/PCFlow.Windows.csproj` referencia `QRCoder` 1.6.0; nenhum `.cs` usa o pacote | N/A | nenhum | PARTIAL |
| E11 | não aparece neste corte | Botões de navegação desabilitados no Windows | N/A | `MainWindow.xaml` (Dispositivos, Recursos e Ajustes com tooltip de próxima etapa) | N/A | nenhum | PARTIAL |
| E12 | não aparece neste corte | Flag de iniciar servidor automaticamente sem efeito de startup | N/A | `Modelos.cs` (`IniciarServidorAutomaticamente`); nenhum registro de inicialização | N/A | nenhum | PARTIAL |

## O que não conta como implementação deste repositório

O release `flow-tutum-next-v2.0.0-alpha.10-as` publica `FlowTutum-Next-2.0.0-alpha.10-AS.apk` (`br.flowtutum`, versionCode 11, versionName `2.0.0-alpha.10-AS`). O manifesto e o `classes.dex` mostram `br.flowtutum.MainActivity`, `android.webkit.WebView` e `JavascriptInterface`. O `version-control-info.textproto` do APK contém `NO_SUPPORTED_VCS_FOUND`. Esse artefato não é o `applicationId` `com.ander.pcflow` deste código e não preenche nenhuma linha desta matriz. A UI principal alvo continua nativa em Compose.

## Manutenção

A coluna de vídeo foi preenchida por T00. Cada slice do plano atualiza a coluna Testes com o nome do teste executado e só então pode trocar o status para `PASS`.

Fundação Web em 2026-11-10 (`cursor/web-pwa-foundation-237f`): `web/` entrega a casca PWA, a tabela de paridade em `docs/web/parity.md` e a sessão que recusa comando sem autenticação e capacidades. A coluna Web desta matriz não vira `PASS`: a casca não fala com um agente. Os testes da fundação são `web/src/parity/parity.test.ts`, `web/src/session/session.test.ts` e `web/e2e/app.spec.ts`. O job de build do Actions passou esses testes; a URL pública ainda responde 404 porque o Pages não foi habilitado.

