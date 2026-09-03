# Defeitos encontrados e corrigidos

Auditoria da versão anterior do PCFlow (commit `d7cfbb4`) a partir dos sintomas
relatados: "está quebrando e não conecta", "botões não funcionais", "no PC passa
da tela e não consigo mudar o tamanho", "tentar conectar no celular dá erro".

Gravidade: **crítica** derruba o app ou impede o uso; **alta** quebra um recurso
principal; **média** atrapalha; **baixa** é acabamento.

---

## #1 — Janela do Windows maior que a tela e impossível de encolher

- **Gravidade:** crítica (sintoma relatado)
- **Onde:** `windows/PCFlow.Windows/MainWindow.xaml`
- **Passos:** abrir o PCFlow num notebook de 1366×768 com escala de 125%.
- **Causa:** a janela era fixa em `Width=1180 Height=760` com `MinWidth=960
  MinHeight=640`. Com 125% de escala a área útil do WPF cai para cerca de
  1092×568, então a janela nascia maior que a tela **e** o mínimo impedia
  redimensionar. Faltava também declaração de DPI no manifesto, o que fazia o
  WPF trabalhar em coordenadas escaladas.
- **Correção:** `AjustarParaTela()` limita largura, altura e posição à
  `SystemParameters.WorkArea` a cada abertura e restauração; os mínimos caíram
  para 600×420 e ainda são reduzidos se a tela for menor; layout responsivo
  recolhe a navegação e o QR em janelas estreitas; `app.manifest` declara
  `PerMonitorV2`. O tamanho escolhido é salvo e revalidado na próxima abertura.
- **Regressão:** verificação manual em diferentes resoluções (a lógica de
  limite é aritmética simples e está isolada em `AjustarParaTela`).

---

## #2 — Segunda instância derrubava o app com exceção não tratada

- **Gravidade:** crítica ("está quebrando")
- **Onde:** `ServidorPcFlow.IniciarAsync`, `MainWindow` (versão anterior)
- **Passos:** abrir o PCFlow duas vezes, ou abrir depois de já estar na bandeja.
- **Causa:** `TcpListener.Start()` lança `SocketException` quando a porta já está
  em uso. A chamada acontecia dentro de um `Loaded += async (...)`, ou seja, um
  `async void`: a exceção subia sem tratamento e fechava o processo.
- **Correção:** mutex global de instância única em `App.OnStartup` com aviso
  amigável; `Iniciar()` devolve `bool` e guarda a mensagem em `UltimoErro` em vez
  de lançar; tratadores globais (`DispatcherUnhandledException`,
  `UnhandledException`, `UnobservedTaskException`) gravam relatório em
  `Documentos\PCFlow` e mantêm o app vivo.
- **Regressão:** `PararEIniciarVariasVezesNaoDeixaEstadoInvalido`,
  `ServidorReiniciaEVoltaAAceitarConexoes`.

---

## #3 — Servidor não podia ser reiniciado

- **Gravidade:** alta
- **Onde:** `ServidorPcFlow`
- **Causa:** o `CancellationTokenSource` era criado no construtor. Depois de
  `DisposeAsync` ele ficava cancelado para sempre; qualquer tentativa de religar
  o servidor criava tarefas que morriam de imediato.
- **Correção:** o CTS passou a ser criado em `Iniciar()` e descartado em
  `PararAsync()`, tornando o par ligar/desligar reutilizável; `ReiniciarAsync()`
  usa isso e está exposto na interface e no menu da bandeja.
- **Regressão:** `PararEIniciarVariasVezesNaoDeixaEstadoInvalido`.

---

## #4 — Descoberta falhava na maioria dos roteadores domésticos

- **Gravidade:** crítica ("o celular não acha o PC")
- **Onde:** `SessaoPcFlow.descobrir` (Android)
- **Causa:** duas falhas somadas. (a) A sonda só era enviada para
  `255.255.255.255`; muitos roteadores descartam esse broadcast limitado e só
  entregam o broadcast dirigido à sub-rede (`192.168.0.255`). (b) Não havia
  `MulticastLock`: sem ele o Wi‑Fi do Android descarta pacotes de broadcast antes
  de entregar ao app, e faltava a permissão `CHANGE_WIFI_MULTICAST_STATE`.
- **Correção:** `Descoberta.procurar` calcula o endereço de broadcast de cada
  interface IPv4 ativa e envia para todos, mais o limitado; segura o
  `MulticastLock` durante a busca; repete a sonda a cada 700 ms, porque UDP se
  perde. A permissão foi adicionada ao manifesto.
- **Regressão:** não coberta por teste automatizado (depende de rede real);
  registrada em `docs/TESTS.md` como verificação manual.

---

## #5 — Sem caminho alternativo quando a descoberta falha

- **Gravidade:** crítica ("dá erro e não tem o que fazer")
- **Onde:** app Android
- **Causa:** a única forma de conectar era pela lista de descoberta. Em rede que
  bloqueia broadcast, o usuário ficava sem saída.
- **Correção:** botão **Digitar IP** aceita `192.168.0.10`, `192.168.0.10:45456`
  e URLs `pcflow://`; botão **Escanear QR**; o Windows ganhou **Copiar endereço**
  e mostra o IP em destaque. O app também abre links `pcflow://` vindos do leitor
  de QR do próprio sistema.
- **Regressão:** `EnderecoPcFlow.interpretar` cobre os formatos aceitos.

---

## #6 — Firewall do Windows bloqueava a porta em silêncio

- **Gravidade:** crítica (causa nº 1 de "acha o PC mas não conecta")
- **Onde:** não existia tratamento
- **Causa:** o Windows bloqueia conexões de entrada para um aplicativo novo sem
  avisar de forma clara. O PCFlow não criava regra nem informava o usuário.
- **Correção:** `IntegracaoWindows.GarantirRegrasFirewall` cria regras de entrada
  para TCP 45456 e UDP 45455 **restritas ao perfil privado** (não abre nada para
  a internet); a tela **Conexão** mostra o estado da regra, oferece elevação sob
  demanda e um botão **Testar porta**.
- **Regressão:** verificação manual (depende do Windows).

---

## #7 — Ponteiro travado em movimentos lentos

- **Gravidade:** alta
- **Onde:** `EntradaWindows.Mover`
- **Passos:** arrastar o dedo devagar no touchpad.
- **Causa:** `dx = (int)x` truncava o deslocamento. Movimentos de menos de 1 px
  por evento viravam zero e eram perdidos, então o ponteiro só andava com gestos
  rápidos.
- **Correção:** acumulador de fração entre eventos em `MoverRelativo`; só a parte
  inteira é enviada e o resto fica para o próximo evento.
- **Regressão:** `MouseTecladoEMidiaChegamNoWindows` confere a soma exata dos
  deslocamentos (12,5 e −4,25 chegam íntegros).

---

## #8 — Eventos de mouse chegavam fora de ordem

- **Gravidade:** alta
- **Onde:** `SessaoPcFlow.enviar` (Android)
- **Causa:** cada evento de toque disparava `escopo.launch { ... }`, ou seja, uma
  corrotina independente por evento. Dezenas por segundo competindo pelo mesmo
  socket produziam ordem imprevisível — o ponteiro tremia e cliques saíam antes
  do movimento.
- **Correção:** fila única com um escritor dedicado (`lacoEscrita`); movimentos
  consecutivos são fundidos num só antes do envio, o que também impede a fila de
  crescer durante arrastes rápidos.
- **Regressão:** `OrdemDosEventosDeEntradaEhPreservada` envia 400 movimentos e
  confere quantidade, ordem e soma.

---

## #9 — Conexão morta não era detectada

- **Gravidade:** alta
- **Onde:** ambos os lados
- **Passos:** desligar o Wi‑Fi do celular por alguns segundos.
- **Causa:** não havia heartbeat. `keepAlive` do TCP usa o padrão do sistema (2
  horas), então o socket ficava "vivo" indefinidamente: o app parecia conectado e
  não respondia a nada.
- **Correção:** `ping`/`pong` a cada 7 s no Android, com queda forçada após 21 s
  sem resposta; o servidor encerra a sessão após 25 s de silêncio. O tempo de ida
  e volta virou o indicador de latência mostrado na tela.
- **Regressão:** `HeartbeatMedeLatencia`; os limites dos dois lados são
  conferidos pelo teste Kotlin `heartbeat cabe dentro do limite de inatividade`.

---

## #10 — Reconexão insistia num IP que já não existia

- **Gravidade:** alta
- **Onde:** `SessaoPcFlow.agendarReconexao` (Android)
- **Causa:** a reconexão repetia o mesmo host indefinidamente. Se o DHCP tivesse
  dado outro IP ao PC, ela nunca voltava.
- **Correção:** o laço de reconexão redescobre o PC pelo **nome da máquina** antes
  de cada tentativa e usa o novo endereço.

---

## #11 — Token perdido quando o IP do PC mudava

- **Gravidade:** alta
- **Onde:** `SessaoPcFlow` (Android)
- **Causa:** o token era gravado com a chave `token_<host>`. Novo IP, nenhum
  token: o app voltava a pedir o PIN sem motivo.
- **Correção:** a chave passou a ser o nome da máquina (estável), com o host como
  reserva para endereços digitados manualmente.

---

## #12 — Gravação concorrente da configuração corrompia o arquivo

- **Gravidade:** alta
- **Onde:** `ArmazenamentoConfiguracao.Salvar`
- **Causa:** cada sessão de celular chamava `Salvar` da sua própria thread, sem
  sincronização. Duas gravações simultâneas causavam `IOException` — não tratada —
  ou deixavam JSON pela metade, e o app abria com a configuração zerada.
- **Correção:** `lock` em toda leitura e gravação; gravação atômica em arquivo
  temporário seguido de `File.Move(overwrite)`; JSON inválido cai para a
  configuração padrão em vez de propagar exceção. A mesma proteção foi aplicada à
  lista de dispositivos dentro do `ServidorPcFlow`.
- **Regressão:** `GravacoesSimultaneasNaoCorrompemOArquivo` (60 gravações em
  paralelo), `ConfiguracaoCorrompidaNaoDerrubaOApp`.

---

## #13 — PIN eterno e sem limite de tentativas

- **Gravidade:** alta (segurança)
- **Onde:** `ServidorPcFlow`
- **Causa:** o PIN era gerado uma vez e valia para sempre; tentativas eram
  ilimitadas. Um milhão de combinações caem em minutos numa LAN.
- **Correção:** `GerenciadorPin` com validade de 3 minutos, rotação automática
  após cada pareamento, comparação em tempo constante e bloqueio de 60 s por IP
  após 5 erros.
- **Regressão:** `BloqueiaForcaBrutaDepoisDeCincoErros`,
  `BloqueioEhPorOrigemENaoGlobal`, `PinEhRotacionadoDepoisDoPareamento`,
  `ForcaBrutaDePinEhBloqueada`.

---

## #14 — Mensagem gigante consumia memória sem limite

- **Gravidade:** alta (segurança)
- **Onde:** leitura da sessão
- **Causa:** `ReadLineAsync` sem limite: uma linha sem `\n` fazia o servidor
  alocar memória até estourar.
- **Correção:** leitura própria com corte em 256 KB por linha; a sessão abusiva é
  encerrada e o servidor continua atendendo os demais.
- **Regressão:** `MensagemGiganteEncerraASessaoSemDerrubarOServidor`.

---

## #15 — Nome de arquivo recebido podia escapar da pasta de destino

- **Gravidade:** alta (segurança) — **encontrado pelo próprio teste**
- **Onde:** `ServicoArquivos.GravarBloco`
- **Passos:** o celular envia um arquivo chamado `..\..\Windows\perigo.txt`.
- **Causa:** `Path.GetFileName` só reconhece o separador do sistema atual. Fora do
  Windows ele devolve a string inteira; a proteção dependia da plataforma em vez
  de depender do dado.
- **Correção:** `ServicoArquivos.NomeSeguro` corta por `/` **e** `\`, remove
  pontos iniciais e troca caracteres inválidos, sempre reduzindo ao último
  componente.
- **Regressão:** `NomeVindoDaRedeEhReduzidoAoArquivo` (5 casos),
  `ArquivoRecebidoSempreCaiNaPastaDeDownloads`.

---

## #16 — Botões da barra lateral do Windows não faziam nada

- **Gravidade:** alta (sintoma relatado)
- **Onde:** `MainWindow.xaml`
- **Causa:** "Dispositivos", "Recursos" e "Ajustes" estavam com
  `IsEnabled="False"` e a dica dizia "entra na próxima etapa". "Conectar novo
  celular" chamava `AtualizarTela()`, que não gerava PIN novo.
- **Correção:** cinco páginas reais implementadas (Início, Dispositivos, Conexão,
  Diagnóstico, Ajustes) com todas as ações funcionando: renomear, bloquear e
  remover dispositivo, regra de firewall, teste de porta, troca de porta,
  reinício do servidor, exportação de diagnóstico e todas as preferências.
  "Gerar novo código" agora renova o PIN e redesenha o QR de verdade.

---

## #17 — Toque no celular sem rolagem, arraste nem botão do meio

- **Gravidade:** alta
- **Onde:** `MainActivity.Touchpad` (Android)
- **Causa:** `detectTapGestures` e `detectDragGestures` encadeados em dois
  `pointerInput` competindo pelos mesmos eventos. Só existiam clique e arraste de
  um dedo; a "rolagem" era um botão que mandava 120 fixo.
- **Correção:** laço próprio de gestos que trata 1, 2 e 3 dedos, com rolagem
  vertical e horizontal contínua, arraste por toque longo, três botões físicos e
  aceleração configurável.

---

## #18 — Serviço em primeiro plano derrubava o app no Android 14+

- **Gravidade:** alta
- **Onde:** `ServicoConexao`
- **Causa:** `startForeground` sem `foregroundServiceType` no Android 14+, e
  `startForegroundService` chamado com o app em segundo plano lança
  `ForegroundServiceStartNotAllowedException`. Sem tratamento, o app fechava
  exatamente ao conectar.
- **Correção:** tipo declarado explicitamente a partir do Android 14, tudo
  envolvido em `runCatching` e notificação com ação "Desconectar". Se o sistema
  recusar o serviço, a conexão segue funcionando com o app aberto.

---

## #19 — Conteúdo embaixo da barra de status no Android 15

- **Gravidade:** média
- **Onde:** `MainActivity`
- **Causa:** com `targetSdk 35` o Android 15 força edge-to-edge. Sem tratar as
  áreas seguras, o cabeçalho ficava sob a barra de status e a barra de abas sob
  os botões de navegação.
- **Correção:** `WindowCompat.setDecorFitsSystemWindows(window, false)` mais
  `Modifier.safeDrawingPadding()` nas duas telas.

---

## #20 — Falha de rede fechava o app sem explicação

- **Gravidade:** média
- **Onde:** app Android
- **Causa:** exceção em corrotina de rede sem tratador global.
- **Correção:** `AplicacaoPcFlow` instala um tratador que registra no logcat e
  encerra a sessão de forma controlada.

---

## #21 — Mensagens de erro não diziam o que fazer

- **Gravidade:** média (é o que transforma "dá erro" em "resolvi")
- **Correção:** cada recusa do servidor tem código e texto próprios ("PIN
  incorreto ou expirado", "Dispositivo bloqueado no PC", "Versões incompatíveis…",
  "Modo somente rede local ativo"); o app explica firewall e mesma rede na falha
  de conexão e mostra o IP do celular com aviso quando o PC está em outra
  sub-rede.

---

## #22 — Enumeração de interfaces de rede a cada segundo

- **Gravidade:** baixa (desempenho)
- **Onde:** `RedeUtil.EnderecoLocal`, chamado pelo relógio da interface
- **Correção:** cache de 5 segundos — curto o bastante para detectar troca de
  Wi‑Fi, longo o bastante para tirar a chamada do caminho quente.

---

## #23 — Dependência do QRCoder declarada e nunca usada

- **Gravidade:** baixa
- **Causa:** o pacote estava no `.csproj` desde o início, mas nenhum QR era
  gerado — a tela prometia um código que não existia.
- **Correção:** `GeradorQr` gera o QR de pareamento com host, porta, PIN e nome
  no formato `pcflow://`, que é exatamente o que o app Android lê.

---

## Defeitos conhecidos em aberto

Nenhum defeito crítico ou alto em aberto. Limitações conhecidas (não são
defeitos, são recursos ausentes) estão em `FEATURE_MATRIX.md`.

- A tela remota envia JPEG completo por quadro. Funciona bem na LAN a 15 fps,
  mas consome mais banda do que um codec com quadros-chave. Melhoria planejada,
  não regressão.
- A verificação em aparelho Android real e em Windows real não pôde ser feita no
  ambiente de desenvolvimento (sem KVM para emulador, sem host Windows). Ver
  `docs/TESTS.md` para o que isso significa na prática.
