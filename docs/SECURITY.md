# Segurança

## Modelo de ameaça

O PCFlow assume uma rede doméstica: quem está na LAN é semi-confiável. O objetivo
é impedir que **outro aparelho na mesma rede** controle o PC sem autorização
explícita, e que dados saiam do computador sem consentimento.

Fora do escopo: atacante com acesso físico ao PC desbloqueado, e malware já
rodando na máquina.

## Autenticação

1. **Pareamento** — PIN de 6 dígitos mostrado no PC, válido por 3 minutos,
   rotacionado após cada pareamento bem-sucedido.
2. **Autorização explícita** — por padrão o PC pergunta antes de aceitar um
   aparelho novo, mesmo com o PIN correto.
3. **Sessões seguintes** — token de 32 bytes aleatórios (`RandomNumberGenerator`)
   por dispositivo, comparado em tempo constante (`FixedTimeEquals`).

Nunca se confia apenas no IP.

## Proteções ativas

| Ameaça | Proteção |
|---|---|
| Força bruta de PIN | 5 tentativas por IP, bloqueio de 60 s, contagem por origem |
| Comparação de token por tempo | comparação em tempo constante |
| Conexão de fora da LAN | modo "somente rede local" ligado por padrão |
| Mensagem gigante (DoS de memória) | corte em 256 KB por linha, sessão encerrada |
| Conexão zumbi ocupando recurso | timeout de 25 s sem mensagem |
| Comando antes do handshake | recusado; o laço de comandos só existe após autenticar |
| JSON malformado | descartado, sessão continua |
| Path traversal na leitura | caminho normalizado e comparado com as raízes autorizadas |
| Path traversal na gravação | `NomeSeguro` corta por `/` e `\`, sem depender do SO |
| Execução arbitrária pelo `app_abrir` | só executa ids que estavam na lista enviada pelo PC |
| Portas abertas para a internet | regra de firewall restrita ao perfil **privado** |

## Controle do usuário

Em **Ajustes**, cada recurso pode ser desligado individualmente: energia,
arquivos, tela remota e área de transferência. Um recurso desligado é recusado no
servidor — não é só a aba sumir do app. Em **Dispositivos**, cada aparelho pode
ser renomeado, bloqueado ou removido, e a sessão dele cai na hora.

## Privacidade

- Nenhuma conta, nenhum servidor externo, nenhuma telemetria.
- O token fica em `SharedPreferences` do app, excluído de backup na nuvem
  (`regras_backup.xml` e `regras_extracao.xml`).
- No PC, a configuração fica em `%AppData%\PCFlow\configuracao.json`.
- **O log nunca registra PIN, token, texto digitado nem conteúdo da área de
  transferência.** Só eventos: conexão, autenticação, comandos por categoria,
  erros.

## Limitações conhecidas — leia

- **O tráfego não é criptografado.** A autenticação impede que um aparelho não
  pareado controle o PC, mas quem já estiver dentro da sua rede Wi‑Fi e
  conseguir capturar pacotes pode ler o que trafega, incluindo texto digitado e
  quadros da tela remota. Numa rede doméstica com WPA2/WPA3 isso está protegido
  pela própria criptografia do Wi‑Fi. **Não use em Wi‑Fi público ou aberto.**
  Criptografia fim a fim (TLS com chave presa ao pareamento) é a próxima
  prioridade de segurança.
- O PIN trafega em claro durante o pareamento, pelo mesmo motivo.
- A chave de assinatura do APK é auto-assinada e acompanha o projeto; serve para
  instalar e atualizar, não para publicar na Play Store.
