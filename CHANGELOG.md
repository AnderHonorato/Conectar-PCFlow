# Changelog

## [1.0.0] — 2026-09-03

Reescrita a partir dos defeitos relatados: não conectava, quebrava, botões
inertes e janela maior que a tela. Os 23 defeitos encontrados estão detalhados
em `BUGS.md` com causa e correção.

### Corrigido — o que impedia o uso

- Janela do Windows nascia maior que a tela e não podia ser encolhida (#1)
- Segunda instância derrubava o aplicativo com exceção não tratada (#2)
- Servidor não podia ser reiniciado depois de parado (#3)
- Descoberta falhava na maioria dos roteadores: faltava broadcast dirigido à
  sub-rede e MulticastLock (#4)
- Sem alternativa quando a descoberta não funcionava (#5)
- Firewall do Windows bloqueava a porta em silêncio (#6)
- Ponteiro travava em movimentos lentos por truncamento (#7)
- Eventos de mouse chegavam fora de ordem (#8)
- Conexão morta não era detectada, sem heartbeat (#9)
- Reconexão insistia num IP que já não existia (#10)
- Token era perdido quando o IP do PC mudava (#11)
- Gravação concorrente corrompia a configuração (#12)
- PIN eterno e sem limite de tentativas (#13)
- Mensagem gigante consumia memória sem limite (#14)
- Nome de arquivo recebido podia escapar da pasta de destino (#15)
- Botões da barra lateral não faziam nada (#16)
- Touchpad sem rolagem, arraste nem botão do meio (#17)
- Serviço em primeiro plano derrubava o app no Android 14+ (#18)
- Conteúdo sob a barra de status no Android 15 (#19)
- Falha de rede fechava o app sem explicação (#20)

### Adicionado

- **Windows:** cinco páginas funcionais (Início, Dispositivos, Conexão,
  Diagnóstico, Ajustes); QR de pareamento; regra de firewall automática restrita
  ao perfil privado; teste de porta; troca de porta; ligar/pausar/reiniciar;
  gerenciar dispositivos; iniciar com o Windows; abrir minimizado; três
  comportamentos para o botão X; menu completo na bandeja; diagnóstico
  exportável; relatório de erro em vez de fechar sozinho
- **Android:** leitura de QR; endereço manual; IP do aparelho com aviso de
  sub-rede diferente; touchpad com gestos de 1, 2 e 3 dedos; ajustes de
  sensibilidade, aceleração e rolagem; teclado completo com modificadores presos
  e atalhos prontos; mídia; energia com confirmação; abrir programas; área de
  transferência; explorador de arquivos com download; tela do PC ao vivo;
  reconexão automática; serviço em primeiro plano com ação de desconectar
- **Protocolo v2:** versão negociada no handshake, heartbeat com latência,
  anúncio de recursos por sessão, transferência em blocos com retomada
- **Testes:** 83 automatizados (75 .NET, 8 Kotlin) incluindo integração com
  sockets reais e verificação cruzada de interoperabilidade entre os dois lados
- **CI:** compila WPF no Linux, revalida no Windows, publica `.exe`, `.zip`
  portátil e os dois APKs com SHA-256

### Alterado

- `PCFlow.Core` separado da interface, tornando o servidor testável fora do
  Windows — é o que permite a suíte de integração
- PIN com validade, rotação e anti-força-bruta
- Modo somente rede local ligado por padrão

## [0.1.0]

Primeira versão. Base de comunicação e interface inicial.
