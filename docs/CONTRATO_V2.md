# Contrato da reformulação v2 — PCFlow

Este documento existe para permitir que várias frentes trabalhem ao mesmo
tempo sem se atropelar. **Ninguém altera as assinaturas daqui sem avisar o
orquestrador.** Quem consome uma API descrita abaixo pode confiar que ela vai
existir exatamente assim.

## Divisão de arquivos (propriedade exclusiva)

| Frente | Arquivos que pode alterar |
|---|---|
| A — Entrada e captura no Windows | `windows/PCFlow.Windows/Core/EntradaWindows.cs`, `ExecutorComandos.cs`, `CapturaTela.cs`, `Modelos.cs` (só campos novos de `MensagemRede`), e apenas o método `TransmitirTelaFluxoAsync` de `ServidorPcFlow.cs` |
| B — Tela de sessão do Android | `android/.../TelaRemota.kt` e novos arquivos em `android/.../sessao/` |
| C — Casca do aplicativo Android | `android/.../PcFlowApp.kt`, `DialogoArquivos.kt`, novos arquivos em `android/.../design/` |
| E — Visual do aplicativo Windows | `windows/PCFlow.Windows/MainWindow.xaml`, `MainWindow.xaml.cs`, `App.xaml`, `ControleRemotoWindow.xaml(.cs)`, `MolduraSessaoWindow.cs`, e novos arquivos de UI em `windows/PCFlow.Windows/` |
| D — Transporte do Android | `android/.../SessaoPcFlow.kt`, `Modelos.kt` |

Ninguém edita arquivo de outra frente. Se precisar de algo de fora, use a API
declarada aqui — ela é garantida.

---

## 1. Protocolo (canal de controle, JSON por linha)

Mensagens novas e campos novos. O servidor ignora campo desconhecido, então
versões diferentes degradam sem quebrar.

### Ponteiro

```json
{"tipo":"mouse_abs","x":0.51,"y":0.33,"monitor":0}
{"tipo":"mouse_move","x":12.0,"y":-4.0}
{"tipo":"mouse_click","botao":"esquerdo","cliques":2}
{"tipo":"mouse_down","botao":"esquerdo"}
{"tipo":"mouse_up","botao":"esquerdo"}
```

- `botao`: `esquerdo` | `direito` | `meio` (também aceita left/right/middle).
- `cliques`: 1 (padrão) ou 2. **2 gera clique duplo com o intervalo do sistema**,
  não dois cliques soltos — é o que faz abrir pasta e selecionar palavra.

### Rolagem

```json
{"tipo":"scroll","delta":-360,"eixo":"vertical"}
{"tipo":"scroll","delta":240,"eixo":"horizontal"}
```

- `eixo` ausente = `vertical` (compatível com a versão anterior).
- `horizontal` usa a roda horizontal do Windows (`MOUSEEVENTF_HWHEEL`).
- `delta` segue a convenção do Windows: 120 = um "clique" de roda.

### Teclado

```json
{"tipo":"texto","texto":"olá ção 🙂"}
{"tipo":"tecla","tecla":"ENTER"}
{"tipo":"tecla","tecla":"C","modificadores":["ctrl"]}
{"tipo":"tecla","tecla":"TAB","modificadores":["alt"]}
```

- `texto`: Unicode literal, incluindo acentos e emoji (pares substitutos).
- `modificadores`: qualquer combinação de `ctrl`, `alt`, `shift`, `win`.
  Pressiona na ordem recebida, aciona a tecla, solta na ordem inversa.
- Os nomes de tecla já existentes continuam valendo.

### Área de transferência

```json
{"tipo":"clipboard_get"}
{"tipo":"clipboard_set","texto":"…"}
{"tipo":"clipboard_modo","modo":"auto"}
```

- `modo`: `desligado` | `auto` | `manual`. Em `auto`, o PC avisa sozinho quando
  a área de transferência dele muda, enviando `{"tipo":"clipboard","texto":"…"}`.

### Tela

O pedido no canal de tela ganha campos opcionais:

```json
{"tipo":"stream","sessaoId":"…","monitor":0,"fps":24,"qualidade":62,"larguraMaxima":1600}
```

- `fps`: 2..30 (era 2..20).
- `larguraMaxima`: 0 = tamanho nativo; senão reduz mantendo proporção. Reduzir
  resolução é o que mais derruba latência em Wi-Fi ruim.

---

## 2. API que o transporte (D) entrega para a tela de sessão (B)

Em `SessaoPcFlow`, tudo já existente continua. **Novo, garantido:**

```kotlin
// ---- ponteiro ----
fun posicionar(x: Double, y: Double, monitor: Int)          // 0..1 normalizado
fun mover(dx: Double, dy: Double)                            // relativo, modo touchpad
fun clicar(botao: String, cliques: Int = 1)                  // "esquerdo"|"direito"|"meio"
fun pressionar(botao: String)
fun soltar(botao: String)
fun rolar(delta: Int, horizontal: Boolean = false)

// ---- teclado ----
fun digitar(texto: String)                                   // Unicode literal
fun teclaEspecial(tecla: String, modificadores: List<String> = emptyList())

// ---- área de transferência ----
fun definirModoClipboard(modo: ModoClipboard)
fun enviarClipboardParaPc(texto: String)
fun puxarClipboardDoPc()                                     // resposta em clipboardRemoto
val clipboardRemoto: StateFlow<String>
val modoClipboard: StateFlow<ModoClipboard>

// ---- tela ----
fun definirPerfilVideo(perfil: PerfilVideo)                  // reabre o stream
val perfilVideo: StateFlow<PerfilVideo>
val quadro: StateFlow<Bitmap?>                               // já existe
val estatisticas: StateFlow<EstatisticasSessao>              // fps e latência reais

fun alternarMonitor(indice: Int)                             // já existe como alterarMonitor
```

Tipos (declarados por D em `Modelos.kt`):

```kotlin
enum class ModoClipboard { DESLIGADO, AUTOMATICO, MANUAL }

enum class PerfilVideo(val fps: Int, val qualidade: Int, val larguraMaxima: Int) {
    RESPOSTA(30, 48, 1280),      // prioriza fluidez
    EQUILIBRADO(24, 62, 1600),
    IMAGEM(15, 82, 0)            // prioriza nitidez
}

data class EstatisticasSessao(
    val quadrosPorSegundo: Int = 0,
    val latenciaMs: Int = 0,
    val bytesPorSegundo: Long = 0
)
```

**Regra de desempenho para D:** decodificar o JPEG fora da thread principal,
reaproveitar o `Bitmap` com `inBitmap`, e nunca deixar quadro velho na fila —
se chegou quadro novo enquanto o anterior não foi desenhado, descarta o antigo.
Latência importa mais que não perder quadro.

---

## 3. API que a tela de sessão (B) entrega para a casca (C)

```kotlin
@Composable
fun TelaRemotaPcFlow(
    estado: EstadoSessao,
    aoFechar: () -> Unit
)
```

Um único ponto de entrada. C abre isso em tela cheia e não sabe nada de gestos.

---

## 4. Regras de qualidade que valem para todas as frentes

1. **Nada só visual.** Botão que aparece funciona, ou fica desabilitado com o
   motivo escrito, ou não existe.
2. **Feedback de toque é transitório.** A onda/ponto nasce onde o dedo tocou,
   acompanha o arrasto enquanto o dedo está na tela e some em no máximo 450 ms
   depois de soltar. Nunca fica parada no centro. Trocar de monitor, girar a
   tela, cancelar o gesto ou cair a sessão limpa o estado.
3. **Nunca segurar tecla ou botão pendurado.** Ao desconectar, soltar tudo.
4. **Coalescer só movimento e rolagem.** `down` e `up` nunca são descartados
   nem reordenados.
5. **Respeitar redução de animações** (`Settings.Global.ANIMATOR_DURATION_SCALE`
   no Android) e alvos de toque de no mínimo 48 dp.
6. **Identidade visual:** grafite/preto de fundo, dourado quente para ação e
   seleção, turquesa para conectado/seguro, vermelho só para erro e ação
   destrutiva. As cores já estão declaradas no topo de `PcFlowApp.kt`.
7. **Nada de "Games".** A aba sai.
8. Português do Brasil em tudo que o usuário lê, sem jargão técnico
   desnecessário e sem texto que promete o que o código não faz.

## 5. Como provar que funciona

Cada frente entrega teste que roda sem hardware:

- A: teste .NET que valida a tradução de mensagem para chamada de entrada
  (clique duplo, roda horizontal, modificadores) e que a captura reduzida
  mantém proporção.
- B e C: teste de unidade Kotlin da lógica pura extraída dos Composables
  (mapeamento de coordenada com letterbox e zoom, máquina de estados do gesto,
  ciclo de vida do feedback de toque).
- D: teste Kotlin do formato das mensagens geradas e da política de descarte
  de quadro atrasado.

`gradle -p android :app:testDebugUnitTest` e
`dotnet test windows/PCFlow.Tests/PCFlow.Tests.csproj` precisam passar.

---

# 6. Sistema visual PCFlow (obrigatório nas duas plataformas)

O aplicativo tem que parecer um produto só, no PC e no celular. Nada de tela
que parece de outra época ao lado de outra. **Nenhuma caixa de diálogo nativa**
— nem `MessageBox` do Windows, nem `AlertDialog` padrão do Android, nem
`Toast`. Tudo é construído dentro do aplicativo, com a cara dele.

## Cores (escuro, as duas plataformas)

| Papel | Valor | Onde usar |
|---|---|---|
| Fundo | `#0E1216` | fundo da janela/tela |
| Superfície | `#161B22` | cartões, painéis, listas |
| Superfície elevada | `#1C232C` | campo de texto, item sob o cursor, menu |
| Borda | `#262D37` | contorno discreto, 1px |
| Borda em foco | `#3A434F` | item focado ou selecionado |
| Ação (dourado) | `#F2AA2E` | botão principal, seleção, destaque |
| Ação pressionada | `#D9931D` | estado ativo do dourado |
| Seguro (turquesa) | `#14D3C3` | conectado, protegido, sucesso |
| Erro | `#FF7A70` | falha, bloqueio, ação destrutiva |
| Texto | `#ECF0F4` | conteúdo |
| Texto secundário | `#98A2AE` | apoio, legenda, ajuda |
| Texto desabilitado | `#5C6672` | controle inativo |

Dourado e turquesa são acentos: aparecem em pouca área e com propósito.
Vermelho **só** para erro e ação destrutiva — nunca decorativo.

## Forma

- Raio de canto: **8** (controle pequeno), **14** (cartão, campo, botão),
  **20** (painel e diálogo), **999** (pílula/ícone redondo).
- Borda de 1px em `Borda`, sem sombra pesada. Profundidade vem da diferença
  entre `Fundo` e `Superfície`, não de sombra preta.
- Espaçamento na escala 4 / 8 / 12 / 16 / 24 / 32. Respiro generoso: painel
  com 24 de folga interna, itens de lista com 16.
- Alvo de toque mínimo de 48 dp no Android e 32 px no Windows.

## Tipografia

- Windows: Segoe UI Variable quando existir, senão Segoe UI.
- Android: a fonte do sistema.
- Escala: 28 (título de tela), 20 (título de seção), 15 (corpo),
  13 (secundário), 11 (legenda). Peso SemiBold só em título.
- Número grande de identidade (ID, código) em fonte monoespaçada, com
  espaçamento entre grupos de dígitos para leitura em voz alta.

## Movimento

- 120–180 ms para estado de controle (hover, pressão, seleção).
- 180–260 ms para abrir/fechar painel, diálogo e menu radial.
- Curva de saída suave (decelerate). Nada de rebote exagerado.
- Feedback de toque some em no máximo 450 ms depois de soltar o dedo.
- **Respeitar redução de animações do sistema**: quando ligada, transição vira
  troca imediata. Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.
  Windows: `SystemParameters.ClientAreaAnimation == false`.

## Diálogos próprios

Todo aviso, confirmação, erro e pedido de senha usa um componente do PCFlow:

- painel centralizado, raio 20, borda 1px, fundo `Superfície`;
- véu escuro por trás (preto a 55%), clique no véu fecha quando a ação não é
  destrutiva;
- título em 20, texto em 15, no máximo dois botões;
- botão de confirmação em dourado; se a ação for destrutiva, em vermelho e
  nunca em foco por padrão;
- abre e fecha em 200 ms com fade e leve subida (8 px).

No Windows isso é uma janela WPF própria sem borda do sistema
(`WindowStyle=None`, `AllowsTransparency=True`), centralizada na janela dona.
No Android é um `Dialog` com `usePlatformDefaultWidth = false` e conteúdo
inteiramente desenhado pelo PCFlow.

## Estados que toda tela precisa cobrir

Normal, carregando (com o que está carregando escrito), vazio (com o próximo
passo sugerido), erro (com o que fazer para sair dele), sucesso, desabilitado
(com o motivo visível). Nada de tela em branco sem explicação e nada de
"carregando" infinito.
