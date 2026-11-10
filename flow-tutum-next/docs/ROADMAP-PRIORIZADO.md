# Roadmap priorizado — Flow Tutum Next

Última revisão: 09/11/2026. Esta lista descreve o trabalho necessário; **itens não marcados como concluídos não estão implementados**. Cada entrega deve ter um PR e teste real. Evitar iniciar várias trilhas simultâneas sem fechar a anterior.

## P0 — Unificar e estabilizar (bloqueadores para novas releases)

| ID | Trabalho | Critério objetivo de aceite | Dependência |
|---|---|---|---|
| P0-01 | Recuperar o projeto Next original da conta AS, comparar com a branch nova e registrar versão | Inventário de arquivos, dependências e estado do Git; backup antes da primeira alteração | PC AS online via Remote Desktop Commander |
| P0-02 | Contrato Android ↔ Windows e compatibilidade de versão | Cliente e servidor concordam em protocolo, capabilities e erros; incompatibilidade oferece instrução clara | P0-01 |
| P0-03 | Portar o painel novo, preservando vídeo real H.264/JPEG | Tela 3440×1440 visível no Galaxy A35; landscape, preencher, zoom, pan sem afetar monitor físico | P0-01 |
| P0-04 | Touchpad real com controle do mouse completo | Esquerdo/meio/direito, duplo clique, scroll nos dois eixos, arraste e soltura ao desconectar | P0-02 |
| P0-05 | Teclado Gboard em tempo real com composição IME | Acentos, emojis, autocorreção, apagar, Enter, texto longo e reconexão sem duplicar caracteres | P0-02 |
| P0-06 | Acesso LAN local controlado pelo Windows | Descoberta funcional, autorização temporária, opção de desligar, revogação imediata, testes com IP/origem inválidos | P0-01 |
| P0-07 | Encerramento explícito e sem reinício automático | Botão Desligar encerra processos da AS; nenhum PowerShell periódico; conta Lil intacta | P0-01 |
| P0-08 | Release unificada Windows + Android | APK assinado com assinatura original, mesmo pacote e dados preservados; EXE compatível; upload GitHub e Drive com hash verificado e link público funcional | P0-01 a P0-07 |

## P1 — Qualidade percebida, performance e confiabilidade

| ID | Trabalho | Critério objetivo de aceite |
|---|---|---|
| P1-01 | Medir desempenho do stream | Registrar FPS, latência, bytes/s, tempo de primeiro frame, CPU/RAM sem logs sensíveis; publicar baseline e comparação |
| P1-02 | Reajuste automático e modos de imagem | Ajustar, preencher, zoom fixo e por pinça; UI sem botões inacessíveis em landscape |
| P1-03 | Reconexão por estado, com cancelamento | Não duplicar sockets nem comandos, não bloquear teclado e soltar mouse durante quedas |
| P1-04 | Onboarding e diagnóstico de Wi-Fi | Instruções práticas em PT-BR, detecção de porta inacessível e aviso claro sobre PC offline/usuário desconectado |
| P1-05 | Acessibilidade | Controle navegável por teclado, rótulos, contraste AA, fontes legíveis e redução de movimento |
| P1-06 | Atualização previsível | Changelog, aviso de versão, comparação e rollback sem reinstalação destrutiva |
| P1-07 | Pipeline de testes de integração | Simulador de cliente + servidor, cenários com timeout, IP errado, token expirado e perda de sessão |

## P2 — Arquitetura para expansão

| ID | Trabalho | Critério objetivo de aceite |
|---|---|---|
| P2-01 | Domínios e módulos isolados | Video, Input, Authentication, DeviceDiscovery, Diagnostics, Settings e Files com interfaces explícitas |
| P2-02 | Contratos tipados e versionados | Schemas JSON documentados e testes de compatibilidade cliente/servidor |
| P2-03 | Persistência com migrações | Preferências locais protegidas; SQLite somente para histórico/perfis que justifiquem persistência relacional |
| P2-04 | Política de permissões para recursos futuros | Pastas autorizadas, acesso de leitura/escrita, proibição de traversal, auditoria e revogação |
| P2-05 | Cobertura e qualidade contínua | Checklists, testes por camada, política de revisão, gates em PR, scanners de vulnerabilidades e segredos |
| P2-06 | Debug e telemetria local | Histórico técnico de erros/latência com exportação voluntária e controles de privacidade |
| P2-07 | Operações e incidentes | Runbook para falha de conexão, stream preto, teclado duplicando, timeout, rollback e recuperação do perfil AS |

## P3 — Recursos novos, somente com fundações estáveis

- Transferência de arquivos com permissões por pasta, limites de tamanho e integridade.
- Perfis de controle, atalhos configuráveis e gestos personalizáveis.
- Seleção explícita de monitor, modos de qualidade adaptativa e controle de áudio, quando suportados e testados.
- Bluetooth: protótipo com transporte e autenticação próprios antes de anunciar como funcional.
- Modo iOS/web: avaliar limitações do navegador antes de prometer paridade com Android.
- Testes de acessibilidade e integração com automação do CI.
- Python para benchmark, geração de relatório técnico e ferramentas de diagnóstico reprodutíveis, se trouxer benefício concreto.

## Critérios de priorização

1. **P0** bloqueia uso seguro, regressão grave, compatibilidade e distribuição.
2. **P1** reduz latência, instabilidade ou atrito comprovado.
3. **P2** evita dívida técnica e permite crescimento sustentável.
4. **P3** adiciona capacidades após as anteriores ficarem estáveis.

Antes de iniciar um item, registrar: **critério de aceite**, **teste de falha**, **dependências**, **risco**, **escopo de arquivos** e **método de rollback**. Se houver bloqueio na conta AS, avançar apenas nos módulos independentes, testes e documentação no GitHub; não tratar esse trabalho como integração final.

## Estado verificado inicialmente

- Novo núcleo de geometria, teclado e autenticação em `feat/flow-tutum-next-core-fullstack` com GitHub Actions de testes independentes.
- PR #4 em rascunho, não mesclada como versão de usuário final.
- APK alpha.10-AS previamente publicado no GitHub/Drive, **sem os novos módulos integrados**.
- Conta Lil fora do escopo, nenhuma nova tarefa agendada no Windows permitida.

## Reunião de produto automática

Cada ciclo registrado pela automação **Evolução Flow Tutum Next** deve:
- Verificar estado e abrir/finalizar um item de backlog.
- Executar a menor melhoria funcional bem testada.
- Relatar as evidências e escolher um único próximo item.
- Não declarar release nova sem EXE + APK integrados e teste de instalação.
