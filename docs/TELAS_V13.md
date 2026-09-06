# Telas V1.3 — especificação a partir dos mockups aprovados

O usuário aprovou um conjunto de mockups (Windows e Android) e pediu que as
funções que aparecem neles sejam implementadas de verdade. Este documento
destrincha cada tela e marca o que o motor atual já sustenta.

Legenda: **[OK]** dá para implementar com o que existe · **[NOVO]** precisa de
código novo mas é viável · **[SERVIDOR]** exige infraestrutura que o PCFlow não
tem (conta de usuário, diretório de contatos, atualização assinada).

---

## Windows

### Início
- ID do computador em número grande, agrupado, com botão de copiar — **[OK]**
- Estado "Pronto para conexões" com ponto colorido — **[OK]**
- QR para o aplicativo, com botão "Mostrar código" revelando o PIN — **[OK]**
- Cartão do sistema: nome da máquina, edição do Windows, processador, memória — **[NOVO]**
  (WMI/registro; nada de dependência externa)
- Dispositivos recentes com estado e menu de ações — **[OK]**
- Atalhos de segurança com interruptores: permitir conexões, exigir
  confirmação, senha de acesso, acesso não supervisionado — **[OK]**
- Cartão "PCFlow V1.3 · uso pessoal" — texto apenas, sem conta — **[OK]**

### Dispositivos
- Busca por nome, ID ou sistema — **[OK]**
- Filtros de status e de sistema — **[OK]**
- Abas Todos / Confiáveis / Recentes / Bloqueados com contagem — **[OK]**
- Tabela: dispositivo, ID copiável, última conexão, sistema, status,
  favorito, ações — **[OK]** (o sistema do dispositivo passa a ser informado
  no handshake pelo aplicativo — **[NOVO]** pequeno no protocolo)
- Painel de detalhe à direita com dados e ações Conectar / Bloquear /
  Revogar / Editar / Remover — **[OK]**
- Apelido e descrição por dispositivo, favorito — **[NOVO]**
- Paginação — **[OK]**
- Miniatura do dispositivo: usar um padrão gerado a partir do ID, não foto — **[NOVO]**

### Segurança
- Permissões de conexão: somente conhecidos / qualquer / somente mediante
  aprovação — **[OK]** (mapeia no que já existe de aceite e lista)
- Comportamento de confirmação: sempre / automático para conhecidos /
  negar automaticamente — **[OK]**
- Senha de acesso não assistido com medidor de força e revelar — **[OK]**
- Lista de dispositivos de confiança com última conexão — **[OK]**
- Visibilidade na rede local — **[OK]**
- Permissões da sessão com interruptores — **[OK]**
- **Atividade de segurança**: conexão aceita, negada, senha alterada,
  com data e origem — **[NOVO]** (registro estruturado, só metadados; nunca
  guardar tecla digitada, conteúdo de tela ou senha)

### Acesso Remoto
- Código de conexão e senha adicional opcional — **[OK]**
- Escolha de monitor com resolução e taxa — **[OK]**
- Qualidade: automática / alta / equilibrada / econômica — **[OK]**
- FPS 15 / 30 / 60 — **[OK]** até 30; 60 só quando a captura sustentar,
  senão o botão fica desabilitado com o motivo escrito
- Modo de toque padrão: Touch ou Touchpad — **[OK]**
- Permissões da sessão — **[OK]**
- Ouvir áudio do computador — **[NOVO]** (captura de laço de áudio; se não
  for entregue, o interruptor não existe, em vez de existir sem função)
- Bloquear tela durante a sessão — **[OK]** (já existe comando de energia)
- **Prévia da sessão ao vivo** com o que o remoto veria — **[NOVO]**
- Iniciar sessão de teste — **[NOVO]** (loopback contra o próprio host)
- Gerar novo código — **[OK]**

### Transferência
- Dois painéis lado a lado, local e remoto, com navegação — **[NOVO]**
  (o canal de arquivos já existe; falta o painel duplo)
- Enviar e baixar entre os painéis — **[OK]**
- Arrastar e soltar da área de trabalho para a janela — **[NOVO]**
- Fila com progresso, velocidade, tempo restante, pausar e cancelar — **[NOVO]**
- Transferências recentes — **[NOVO]**

### Configurações
- Tema claro / escuro / automático — **[NOVO]** (hoje só escuro)
- Cor de destaque com cinco opções — **[NOVO]**
- Iniciar com o Windows — **[OK]**
- Descoberta na rede local — **[OK]**
- Atalhos de teclado configuráveis — **[NOVO]**
- Iniciar minimizado, mostrar dispositivos offline, sons — **[NOVO]**
- Notificações de conexão, transferência e novos dispositivos — **[NOVO]**
- Idioma — **[NOVO]** só português por enquanto; o seletor só existe quando
  houver um segundo idioma de verdade
- Verificar atualizações — **[SERVIDOR]**
- Coletar dados de uso anônimos — **fora do produto**: não haverá telemetria
- Sobre, licença, site — **[OK]**

---

## Android

### Início
- Campo "Digite o ID do dispositivo" com seta de ação — **[OK]**
- Escanear QR e Conectar — **[OK]**
- Meus dispositivos com miniatura, ID, estado e favorito — **[OK]**
- Faixa de versão — **[OK]**
- Barra inferior: Início / Recentes / Dispositivos / Contatos — parcial

### Recentes
- Histórico de sessões com data, duração e resultado — **[NOVO]**

### Dispositivos
- Busca e filtro — **[OK]**
- Seções Favoritos / Na mesma rede / Todos — **[OK]**
- Conectar e Renomear direto no item — **[OK]**
- Botão flutuante Adicionar dispositivo — **[OK]**

### Contatos
- Lista de pessoas, presença, grupos, convidar, solicitações, chat — **[SERVIDOR]**
  Exige conta, diretório de usuários e presença. **Não existe no PCFlow.**
  A aba não entra enquanto não houver esse serviço: botão que não funciona é
  pior que botão ausente. O lugar dessa aba fica com **Recentes**.

---

## Decisões

1. **Sem conta e sem nuvem obrigatória.** Os mockups mostram "João Silva ·
   Conta gratuita"; o PCFlow continua funcionando sem cadastro. Onde o mockup
   mostra perfil, mostramos a identidade da máquina.
2. **Contatos e chat entre pessoas ficam de fora** até existir servidor de
   diretório. É a única parte dos mockups que não tem como ser honesta agora.
3. **Nenhuma telemetria**, mesmo o mockup trazendo o interruptor.
4. Miniaturas de dispositivo são geradas a partir do ID (padrão determinístico),
   não fotos de banco de imagem.
5. Todo interruptor da tela mexe em configuração real do servidor. Nada de
   controle decorativo.
