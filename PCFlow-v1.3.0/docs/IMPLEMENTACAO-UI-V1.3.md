# PCFlow V1.3 — implementação visual e funcional

Esta entrega mantém todo o trabalho dentro de `PCFlow-v1.3.0/` e não substitui a versão existente na raiz do repositório.

## Android

### Início
- conexão por ID;
- descoberta automática na LAN;
- leitura de QR Code PCFlow;
- PIN e senha de acesso não assistido;
- dispositivos salvos;
- favoritos;
- indicadores online/salvo;
- persistência local.

### Recentes
- histórico local de conexões e falhas;
- filtros Hoje / Esta semana / Todos;
- reconexão ao computador salvo;
- horário relativo da sessão.

### Dispositivos
- pesquisa por nome e ID;
- agrupamentos Favoritos / Na mesma rede / Todos;
- conectar;
- favoritar;
- renomear;
- remover do catálogo local.

### Contatos
- grupos e favoritos;
- criação de contato;
- vínculo de contato a um dispositivo;
- compartilhamento de convite `pcflow://connect`;
- recebimento de convite por deep link;
- aceitar/recusar solicitação compartilhada;
- conectar ao dispositivo do contato;
- chat para o PC conectado.

### Sessão remota
- tela em tempo real;
- toque direto com mapeamento proporcional/letterbox;
- touchpad relativo;
- modo apenas visualizar com zoom/pan;
- clique simples, duplo e direito;
- arrastar com mouse down/move/up;
- feedback visual do toque;
- troca de monitor;
- teclado e atalhos;
- clipboard;
- arquivos remotos;
- mídia/volume;
- bloquear PC e desligar monitor;
- ações rápidas de navegador/Windows;
- menu flutuante curvo inspirado no conceito visual;
- encerramento confirmado.

## Windows

### Início
- ID, IP/endereço e estado do servidor;
- QR Code e PIN temporário;
- copiar ID/código;
- gerar novo código;
- dispositivos recentes;
- resumo real de sistema;
- controles rápidos de segurança.

### Dispositivos
- pesquisa;
- filtros de status/favoritos;
- detalhes;
- favorito;
- renomear;
- bloquear/liberar;
- revogar acesso;
- fluxo de pareamento de novo dispositivo.

### Segurança
- política de acesso interativo;
- senha de acesso não assistido;
- medidor de força da senha;
- permissões de tela, entrada, clipboard, energia e arquivos;
- lista de dispositivos confiáveis;
- descoberta LAN;
- moldura durante sessão;
- registro de atividade e notificações.

### Acesso Remoto
- seleção de monitor;
- qualidade/FPS/modo de toque persistidos;
- permissões da sessão;
- prévia real da tela;
- janela de sessão de teste;
- geração de novo código.

### Transferência
- navegação real no sistema de arquivos local;
- área compartilhada PCFlow;
- enviar/baixar arquivos e pastas;
- cópia assíncrona;
- progresso/estado;
- notificações;
- servidor TLS de arquivos existente preservado para o Android.

### Configurações
- tema claro/escuro/automático;
- cinco cores de destaque;
- iniciar com Windows;
- minimizar para bandeja;
- descoberta LAN;
- preferências e notificações persistentes;
- idioma salvo como preferência;
- atalhos exibidos;
- verificação de atualizações via página de releases;
- bandeja do sistema.

## Segurança mantida

- TLS no controle, tela e arquivos;
- pinagem da identidade do host no Android;
- aprovação interativa para acesso por ID;
- QR/PIN temporário;
- senha não supervisionada com hash/salt;
- bloqueio e revogação de dispositivos;
- restrição atual do servidor à rede local.

## Validação

A estrutura foi revisada para manter os novos arquivos exclusivamente dentro da pasta V1.3. O script `build-v1.3.ps1` compila especificamente esta pasta sem depender dos projetos antigos da raiz.

Como a regra deste trabalho proíbe alterar o workflow da raiz do repositório, o workflow automático existente continua validando apenas a versão antiga da raiz. Portanto, um build verde desse workflow não deve ser confundido com validação binária desta nova interface. Antes de publicar uma nova entrega EXE/APK, execute `PCFlow-v1.3.0/build-v1.3.ps1` em um ambiente com .NET 8, Android SDK/JDK 17 e Gradle disponíveis.
