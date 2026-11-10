# O que este navegador faz

Apêndice G da reconstrução. A tabela viva do cliente está em `web/src/parity/parity.ts` e na tela homônima da PWA. Bluetooth não está marcado como suporte universal.

O protocolo compartilhado do slice 1 ainda não está no repositório. O cliente fala com o agente por um adaptador WebSocket provisório em `web/src/transport/protocolAdapter.ts`. Quando o schema chegar, a troca fica nesse arquivo.

| ID | Capacidade | Suporte | Limite |
|----|------------|---------|--------|
| connection-list | Lista de computadores | precisa de conexão | O navegador não varre a rede. A lista só guarda endereços que chegaram a abrir o WebSocket. |
| status | Estado da sessão | disponível | A tela começa desconectada. Conectado só existe depois de autenticar e de receber capacidades. |
| lan | Endereço WebSocket | precisa de conexão | Sem mDNS e sem UDP. Página HTTPS exige wss://. ws:// fica restrito ao desenvolvimento. |
| discovery | Descoberta automática | indisponível | O navegador não anuncia nem escuta a descoberta do agente na LAN. |
| bluetooth | Bluetooth | dependente do navegador | Não vale para todos os navegadores. Este cliente não usa Bluetooth como transporte do controle. |
| touchpad | Touchpad | precisa de conexão | A área ocupa a maior parte da tela. O movimento só sai com a sessão pronta. |
| scroll | Trilho de scroll | precisa de conexão | Setas e arraste no trilho. A direção segue o ajuste local de scroll natural. |
| mouse-buttons | Botões do mouse | precisa de conexão | Esquerdo, meio e direito, com press e release separados quando a sessão está pronta. |
| keyboard | Teclado | precisa de conexão | Não há alfabeto desenhado. As letras ficam no teclado do sistema e saem em bloco. Atalhos globais fora do campo não são capturados. Teclas não são registradas em log. |
| media | Mídia | precisa de conexão | Reproduzir ou pausar, faixa, volume e parar. Sem botão sem nome. |
| windows | Janelas e aplicativos | precisa de conexão | A lista vem do agente. Sem sessão, a tela diz que não há aplicativos. |
| browser | Navegador remoto | precisa de conexão | Voltar, avançar, nova aba, fechar, zoom, recarregar e início. Comandos sem rótulo não entram. |
| power | Energia | precisa de conexão | Desligar, repouso, reiniciar e encerrar sessão só com confirmação e sessão pronta. |
| clipboard | Área de transferência | depende de permissão | Nada é lido nem escrito. A permissão do navegador não é pedida neste corte. |
| gyro | Gyro | dependente do navegador | O botão não lê o sensor. Giroscópio no navegador depende de gesto, permissão e sessão. |
| settings | Ajustes locais | disponível | Lista vertical com seções, interruptores e controles deslizantes. Não é grade de cartões. |
| account | Conta | não configurado | O backend de conta é dependência externa do dono. Não há formulário de login. |
| billing | Pro | não configurado | Três períodos, sem quantia e sem compra. O pagamento não está configurado. |
| wake | Manter a tela acesa | dependente do navegador | Usa Wake Lock só onde o navegador oferece a API e o usuário ligou o ajuste. |
| orientation | Trava de retrato | dependente do navegador | Pede a trava de orientação só onde a API existe. Se o navegador recusar, o ajuste explica. |
| volume-keys | Botões de volume do aparelho | indisponível | O navegador não entrega esses botões ao site. O volume do PC fica no painel de mídia. |
| install | Instalação | disponível | Manifesto e service worker guardam a casca do app. Não mantêm o controle em segundo plano. |
