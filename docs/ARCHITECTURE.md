# Arquitetura

## Ideia central

Todo o comportamento fica em **PCFlow.Core**, um projeto `net8.0` sem nenhuma
dependência de Windows. A interface WPF e o acesso ao sistema ficam em
**PCFlow.Windows**, que implementa as interfaces do núcleo.

Isso não é purismo: é o que torna o produto testável. `dotnet test` roda o
servidor, o pareamento, a segurança e a transferência de arquivos em qualquer
sistema operacional, com sockets de verdade, porque nada disso depende de
`user32.dll`.

```
┌──────────────────────────┐        LAN         ┌──────────────────────────┐
│  Windows                 │                    │  Android                 │
│                          │  UDP 45455 sonda   │                          │
│  ServicoDescoberta   ◄───┼────────────────────┼──  Descoberta            │
│                          │  TCP 45456 sessão  │                          │
│  ServidorPcFlow      ◄───┼────────────────────┼──  SessaoPcFlow          │
│   └ SessaoCliente        │   JSON por linha   │     ├ fila de escrita    │
│      ├ GerenciadorPin    │                    │     ├ heartbeat          │
│      ├ ServicoArquivos   │                    │     └ reconexão          │
│      └ ServicosPlataforma│                    │                          │
│         (interfaces)     │                    │  Compose: Touchpad,      │
│           ▲              │                    │  Teclado, Comandos,      │
│           │ implementa   │                    │  Arquivos, Tela          │
│  EntradaWindows,         │                    │                          │
│  CapturaTelaWindows,     │                    │  ServicoConexao (FGS)    │
│  EnergiaWindows, …       │                    │                          │
└──────────────────────────┘                    └──────────────────────────┘
```

## Windows

| Arquivo | Papel |
|---|---|
| `Protocolo.cs` | constantes, modelo de mensagem, JSON |
| `ServidorPcFlow.cs` | ciclo de vida, aceitação, autenticação, dispositivos |
| `SessaoCliente.cs` | uma conexão: leitura limitada, timeout, comandos |
| `ServicoDescoberta.cs` | responde às sondas UDP |
| `GerenciadorPin.cs` | PIN com validade, rotação e anti-força-bruta |
| `ServicoArquivos.cs` | sandbox de caminhos, blocos, nome seguro |
| `Configuracao.cs` | persistência atômica e sincronizada |
| `Teclas.cs` | nomes de tecla → Virtual-Key (testável, sem P/Invoke) |
| `RedeUtil.cs` | endereços locais com cache, classificação de rede privada |
| `Registro.cs` | log em memória + exportação |
| `Plataforma.cs` | as interfaces que o Windows implementa |

Na camada Windows: `EntradaWindows` (SendInput com acumulador de fração),
`CapturaTelaWindows` (GDI + JPEG), `EnergiaWindows`, `AreaTransferenciaWindows`
(thread STA dedicada), `LancadorWindows` (atalhos do Menu Iniciar, execução só
por id da lista), `IntegracaoWindows` (firewall e inicialização automática).

A interface é uma única janela com cinco páginas alternadas por visibilidade —
escolha deliberada: menos peças móveis, menos chance de quebrar em tempo de
execução, e o dimensionamento fica todo concentrado em `AjustarParaTela`.

## Android

| Arquivo | Papel |
|---|---|
| `rede/Protocolo.kt` | espelho do protocolo + leitura de `pcflow://` |
| `rede/Descoberta.kt` | broadcast dirigido + MulticastLock |
| `rede/SessaoPcFlow.kt` | conexão, fila ordenada, heartbeat, reconexão |
| `rede/RedeLocal.kt` | IP do aparelho e checagem de sub-rede |
| `ServicoConexao.kt` | Foreground Service tolerante a recusa do sistema |
| `ui/Touchpad.kt` | gestos de 1, 2 e 3 dedos num laço só |
| `ui/TelaConectar.kt`, `ui/TelaControle.kt` | as duas telas |
| `ui/Preferencias.kt` | ajustes locais |

### Por que uma fila de escrita

Um arraste gera dezenas de eventos por segundo. Uma corrotina por evento
(como era antes) não garante ordem no socket. A fila com um escritor único
garante ordem e ainda funde movimentos consecutivos, o que mantém a latência
baixa mesmo em gesto rápido.

## Decisões e alternativas descartadas

- **JSON por linha em vez de binário.** Depuração trivial, custo irrelevante na
  LAN para eventos de entrada. A tela remota, que é o caso pesado, usa base64
  dentro do mesmo canal — simplicidade valeu mais que a economia de ~33%.
- **Uma janela com páginas em vez de navegação WPF.** Menos superfície para
  falhar em tempo de execução; o problema relatado era justamente de layout.
- **JPEG por quadro em vez de codec com quadros-chave.** Não exige dependência
  nativa e funciona bem a 15 fps na LAN. É o ponto de melhoria mais claro.
- **Sem ViGEm/gamepad virtual.** Exigiria instalar driver de terceiros; ficou de
  fora em vez de virar botão que não funciona.
