# Threat model

Estado em 2026-11-10: a lista completa da seção 9 do prompt mestre continua na task T61. Este arquivo existe para não perder a decisão do dono sobre o módulo Chefinho (ADR-019). O restante das ameaças do controle remoto entra aqui quando T61 for executada.

## Dados do controle remoto

O log e o transporte de controle não guardam teclado, clipboard, título de janela nem imagem transferida (ADR-011). Isso não muda com o slice 15.

## Companion / Chefinho — dados pessoais sensíveis

O módulo é opt-in e o titular é quem instalou o APK no próprio aparelho. Não é ferramenta para observar outra pessoa. Enquanto o dono não entregar o servidor de ingestão, nada sai do aparelho.

Ativos, se a fonte correspondente for ligada:

| Fonte | Dado | Sensibilidade |
|-------|------|----------------|
| Notificações | remetente, app e texto da notificação | alta; pode incluir mensagem privada |
| Uso de apps | pacote e tempo em primeiro plano | média |
| Bateria | nível e estado de carga | baixa |
| Localização | posição, só se o toggle próprio estiver ligado | alta |
| Acessibilidade | texto visível na tela, só com o serviço do sistema habilitado pelo usuário | alta |
| Janela ativa no Windows | título e nome do app, mais tempo de uso | média; pode revelar documento ou site |
| Captura de tela | imagem da área de trabalho, só sob pedido | alta |

Teclado digitado e clipboard não são fontes. Se a árvore de acessibilidade ou um screenshot mostrar um campo de senha, um rascunho ou a área de transferência, o coletor descarta esse trecho. Registrar isso seria violação do ADR-011 e do ADR-019.

### Ameaças

| ID | Ameaça | Controle exigido quando o slice 15 for implementado |
|----|--------|-----------------------------------------------------|
| C1 | Fonte ligada sem gesto do dono | Default desligado. Toggle por fonte. Indicador visível enquanto qualquer fonte estiver ligada. |
| C2 | Acessibilidade lendo a tela sem o consentimento do sistema | O serviço só coleta se o Android mostrar o `AccessibilityService` habilitado pelo usuário e o toggle do app também estiver ligado. |
| C3 | Captura contínua da tela do PC | Não há timer. Uma captura equivale a um pedido explícito. |
| C4 | O controle remoto para de funcionar sem o módulo | Nenhum tipo do protocolo de sessão importa o Chefinho. Teste de isolamento impede a dependência. |
| C5 | Teclado ou clipboard no cofre ou no log | Proibido nas duas vias. Teste recusa campo de edição, senha e clipboard. |
| C6 | Cofre em texto claro | Cifra em repouso. Chave no Keystore do Android e equivalente no Windows. |
| C7 | Envio em claro, ou envio para um host inventado | `CompanionIngestPort` desconfigurada não abre socket. Quando o dono entregar o servidor, o envio é cifrado em trânsito. Até lá o estado é dependência externa, não fila silenciosa. |
| C8 | Retenção indefinida | Prazo configurável. Botão de apagar tudo limpa o cofre local sem apagar o token de pareamento do PC. |
| C9 | Distribuição pela Play Store com listener de notificação e acessibilidade | O pacote que inclui o módulo sai como APK direto. |
| C10 | Outra pessoa na LAN usa o controle para ligar a coleta | Os toggles não são comando remoto. A sessão de controle não tem mensagem que ligue fonte nem peça screenshot. |
| C11 | Log de diagnóstico com payload do módulo | Log do controle continua redigido. Evento do Chefinho não entra nesse log. |
| C12 | Screenshot ou notificação com segredo | Tratar como dado sensível: cifra, retenção e apagamento. Não copiar para analytics. |

Nenhum desses controles está implementado. O slice 15 do plano (`T72`–`T77`) é o lugar em que passam a existir, depois dos slices 1–14.
