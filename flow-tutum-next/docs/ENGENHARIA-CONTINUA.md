# Manual de engenharia contínua — Flow Tutum Next

> Produto ÚNICO: aplicativo Android `br.flowtutum` + servidor/EXE Windows na conta **AS**. Nenhum aplicativo PCFlow paralelo e nenhuma alteração na conta **Lil**.

## Missão
Construir e manter um sistema de controle remoto de PC confiável, seguro, acessível, performático, fácil de evoluir e com entregas verificáveis. O produto precisa funcionar no Galaxy A35 em vertical e horizontal, incluindo um monitor UWQHD 3440 × 1440, sem alterar a resolução física do computador.

**Estado atual (09/11/2026):** o Next instalado na AS está na versão alpha.10 e seus fontes principais estão no PC local; a branch `feat/flow-tutum-next-core-fullstack` contém módulos novos testados, mas **não está integrada ao executável nem ao APK**. A release alpha.10 existente NÃO representa esta branch. É proibido anunciar uma atualização completa até compilar, instalar em teste e validar o conjunto real.

## Fonte de verdade e fluxo de trabalho
1. Repositório: `AnderHonorato/Conectar-PCFlow`. Usar issue #3 como iniciativa de unificação e PR #4 como referência dos módulos Next. O código legado PCFlow é somente histórico.
2. Quando conectado, acessar os arquivos locais da conta **AS** somente via @Remote Desktop Commander; nenhum acesso à conta Lil. Não usar Work. Projeto: `C:\Users\AS\Documents\Ander Flow\Flow Tutum\reformulacao-2.0`.
3. Quando o PC estiver offline, implementar apenas mudanças independentes e testáveis no GitHub, preservando branch/PR. **Não presumir** que servidor, vídeo, touch, Bluetooth ou APK foram testados.
4. Cada ciclo deve escolher a prioridade mais alta que caiba em uma alteração coesa, implementar código, testes, documentação mínima e apresentar commit/PR e resultados dos checks.
5. Não criar tarefas locais agendadas, watchdogs nem janelas periódicas de PowerShell. Inicialização somente manual, salvo nova ordem explícita. Desligar deve encerrar servidor, liberar botões e revogar as sessões.

## Gestão do produto
- **Backlog:** severidade, prioridade, impacto, risco, esforço estimado, dependências, critérios de aceite e evidências. Organizado por épicos; sem listas enormes de ideias sem definição de pronto.
- **Histórico:** PRs pequenas e revisáveis; commit com escopo (`feat`, `fix`, `security`, `refactor`, `perf`, `test`, `docs`, `ci`); CHANGELOG por versão real.
- **Roadmap:** por entregas verificáveis, sem prazos irreais. Preferir estabilidade antes de multiplicar funcionalidades.
- **Versionamento:** SemVer para Windows/API; `versionCode` Android sempre crescente; compatibilidade explicada no contrato entre o APK e o servidor.

## Arquitetura sugerida
| Camada | Responsabilidades e requisitos |
|---|---|
| Frontend `HTML/CSS/JS` | UI mobile-first, acessibilidade, semântica, toque, tela em paisagem, Canvas/vídeo, zoom e mapeamento de coordenadas, estados de conexão, design tokens, i18n futura |
| Cliente Android | Descoberta LAN com fallback por IP, Gboard/IME, rede e reconexão, preferências, gestão do ciclo de vida, assinatura e atualização do mesmo pacote |
| Backend Windows `.NET/C#` | API HTTP local, WebSocket para comandos, sessão do usuário AS, captura H.264/JPEG, autorização, limites, cancelamento, logs e desligamento controlado |
| Módulos auxiliares `Python` | Somente se houver uso mensurável: análise de desempenho, geração de relatórios de testes, benchmarking ou scripts multiplataforma |
| Persistência | Para preferências pequenas, armazenamento local protegido. SQLite e migrações versionadas **somente se** histórico, auditoria ou dados relacionais justificarem; nunca armazenar tokens em texto claro |
| DevOps e QA | GitHub Actions, análise estática, builds reprodutíveis, suíte unitária/integração/E2E, geração de APK assinado, artefatos rastreáveis e checksum |

### Contrato de comunicação
- Documentar versão do protocolo e capabilities negociadas (vídeo, toque, teclado, transferência de arquivos, sessão).
- Schemas rigorosos de payload, tamanho máximo, sequência, timeout e tratamento de mensagens fora de ordem.
- WebSocket só recebe ações após autenticação e autorização da sessão ativa, inclusive em reconexões e troca de IP.
- Acesso local sem PIN deve ser um modo explicitamente habilitável pelo Windows e **não é autenticação de identidade**: visitantes na mesma sub-rede podem acessá-lo. Restringir interface/rede e permitir desativação imediata.
- Bluetooth só entra no roadmap após prova de transporte end-to-end; não confundir descoberta Bluetooth com controle funcional.

## Padrões de qualidade

### Definition of Ready
- Problema e objetivo claros.
- Caso de uso real e impacto observável.
- Contrato, dependências e riscos identificados.
- Critérios de aceite testáveis (inclusive cenário de erro e caso offline).

### Definition of Done — obrigatória
- Código rodando onde o recurso realmente existe; teste automatizado demonstra pelo menos um caso de sucesso e um de falha.
- Lint/checagem de tipos, build e testes afetados passam.
- Sem regressões em outros fluxos; teste de reconexão e segurança quando a mudança envolve comunicação.
- Revisão de segurança proporcional: tokens, dados sensíveis, origin, rate limit, dependências, validação.
- Documentação do comportamento/limite atualizada; versão e changelog quando aplicável.
- Evidências: commit/PR + link do workflow e hash SHA-256 do binário, se gerado.
- Feature flags/rollback para mudanças arriscadas; não disponibilizar código experimental como atualização estável.
- Se o PC estiver offline, marcar como **código preparado**, não como instalação, APK atualizado ou funcionalidade validada fisicamente.

### Matriz mínima de testes
1. **Frontend:** landscape/portrait; Galaxy A35; teclado virtual; touchpad; acessibilidade (tabulação, rotulagem, contraste, redução de movimento); responsividade.
2. **Geometria/vídeo:** 3440×1440, proporção 21:9; ajustar/preencher; pan/pinch zoom; bordas; fps, bitrate, latência, memória.
3. **Teclado:** Gboard, composição de acentos, emoji, autocorreção, Backspace, Enter, edição intermediária, digitação em tempo real.
4. **Mouse:** esquerdo, direito, meio, duplo clique, segurar/soltar, scroll horizontal/vertical, interrupção Wi-Fi durante arraste.
5. **Rede/servidor:** descoberta, reconexão, expiração/revogação de credenciais, origem, IP, portabilidade, sessão AS, desativação de controle.
6. **Segurança:** controle sem autorização, replay de comandos, flood/rate limit, permissões de pastas, atualizações de dependências e logs sem segredos.
7. **Release:** upgrade sem desinstalar nem perder dados, mesmo applicationId e certificado, versão correta, APK/EXE de uma mesma revisão compatível.

## Pipeline mínimo de CI/CD
1. Análise de sintaxe, lint, formatação e tipos.
2. Testes unitários em JavaScript e .NET; testes Android e schemas quando aplicável.
3. Testes de integração de HTTP/WebSocket e regressão em ambiente de testes controlado.
4. Build reproduzível Windows e Android; SAST/análise de dependências/segredos.
5. Relatório de testes, hashes e provenance dos artefatos.
6. **Release somente após todos os gates relevantes passarem**; não usar APK debug como entrega final. Assinar com chave existente da AS, sem exportar o keystore.
7. Publicar a mesma versão no GitHub Release e Drive; conferir tamanho/hash, permissão de acesso e link HTTPS efetivamente acessível. Conservar a versão anterior e plano de rollback.

## Observabilidade
- Logs estruturados localmente: data, tipo de evento, requestId, versão, resultado, duração, motivo técnico, sem IP completo/token/texto digitado quando não for necessário.
- Métricas de fluxo: conexão bem-sucedida, reconexões, latência de entrada, início do stream, frames perdidos, travamentos e consumo de memória/CPU. Opt-in para telemetria remota, se houver.
- Tratamento de exceções sem ocultar erros do usuário; status de conexão preciso no painel.
- Health checks de infraestrutura não equivalem a controle funcional; validar separadamente o usuário conectado e as permissões.

## Segurança e privacidade
- Menor privilégio, escopo exclusivo AS, tráfego privado na LAN, rate limits e expiração de sessão, revogação imediata.
- Tokens aleatórios criptograficamente seguros e protegidos em repouso; nunca incluir tokens, senhas, keystores ou dados do teclado em logs/commit/release.
- Política CSP para conteúdo web; limites de payload e prevenção de origens externas.
- Menus claros para **Desligar / Bloquear / Revogar / Ver sessões**.
- Antes de adicionar transferência de arquivos ou execução de processos, exigir autorização granular, auditoria e proteção contra traversal/exfiltração.

## Modelo de registro do ciclo de implementação
```text
CICLO: data/hora (Brasília)
BASE: branch, commit inicial, estado do PC AS
OBJETIVO: requisito, prioridade, critérios de aceite
ALTERAÇÕES: caminhos e resumo técnico
VALIDAÇÃO: comandos executados, resultado e provas
SEGURANÇA: impactos e revisão
ENTREGA: PR/commit; release e Drive somente se verificados
BLOQUEIOS: o que não foi possível validar
PRÓXIMO: um item específico e executável
```

## Condutas proibidas
Não mesclar o legado PCFlow como segundo aplicativo, não alterar a conta Lil, não fingir teste físico, não publicar link não testado, não instalar serviços ocultos, não iniciar tarefas PowerShell recorrentes, não perder assinatura Android e não anunciar um APK diferente do compilado e validado.
