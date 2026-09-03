# Arquitetura do PCFlow

## Visão geral

O PCFlow é dividido em dois executáveis independentes:

- **PCFlow.Windows**: host local e executor autorizado de comandos do sistema.
- **PCFlow Android**: cliente/controle remoto.

## Portas

- UDP `45455`: descoberta LAN.
- TCP `45456`: sessão de controle.

## Descoberta

O Android transmite `PCFLOW_DISCOVER_V1` por broadcast. O host responde com JSON contendo nome do computador, porta e versão do protocolo.

## Pareamento

Primeiro acesso:

1. Windows gera PIN aleatório de 6 dígitos.
2. Android envia ID persistente do aparelho, nome e PIN.
3. Host valida o PIN e gera token aleatório de 256 bits.
4. Host persiste ID + token em `%APPDATA%/PCFlow/configuracao.json`.
5. Android persiste o token em `SharedPreferences`.
6. O PIN é rotacionado após autorização.

A versão alpha ainda não cifra o transporte. Isso deve ser corrigido antes de exposição fora da LAN ou release estável.

## Protocolo

Mensagens usam JSON delimitado por quebra de linha. Tipos implementados:

- `ola`
- `mouse_move`
- `mouse_click`
- `scroll`
- `texto`
- `tecla`
- `media`
- `power`

## Windows

A entrada de mouse/teclado usa `SendInput` de `user32.dll`. Controles de mídia usam virtual keys do Windows. Energia usa APIs do Windows e comandos nativos.

A janela WPF e o servidor estão desacoplados o suficiente para que ocultar a janela não encerre o servidor. A próxima etapa deve extrair o servidor para um processo/serviço ainda mais isolado.

## Android

Jetpack Compose renderiza a interface. `SessaoPcFlow` mantém estado com `StateFlow` e socket TCP. `ServicoConexao` usa Foreground Service durante conexão ativa.
