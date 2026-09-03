# Matriz de funcionalidades

Regra desta tabela: **nada é marcado como pronto sem implementação real e sem
estar acessível na interface.** "Testado" significa coberto por teste
automatizado que roda em CI, não "eu olhei e parecia certo".

## Conexão

| Recurso | Windows | Android | Testado | Estado |
|---|---|---|---|---|
| Descoberta automática (UDP broadcast) | ✅ | ✅ | parcial¹ | pronto |
| Broadcast dirigido à sub-rede + MulticastLock | ✅ | ✅ | manual | pronto |
| Pareamento por PIN (6 dígitos, 3 min) | ✅ | ✅ | ✅ | pronto |
| Pareamento por QR Code | ✅ gera | ✅ escaneia | parcial² | pronto |
| Link `pcflow://` do leitor de QR do sistema | ✅ | ✅ | ✅ formato | pronto |
| Endereço manual (IP ou IP:porta) | ✅ mostra/copia | ✅ | ✅ formato | pronto |
| Token persistente por dispositivo | ✅ | ✅ | ✅ | pronto |
| Reconexão automática com espera crescente | — | ✅ | manual | pronto |
| Redescoberta pelo nome quando o IP muda | — | ✅ | manual | pronto |
| Heartbeat + latência na tela | ✅ | ✅ | ✅ | pronto |
| Modo somente rede local | ✅ | — | ✅ | pronto |
| Aviso de sub-rede diferente | — | ✅ | ✅ | pronto |
| Vários celulares ao mesmo tempo | ✅ | ✅ | ✅ | pronto |

¹ o servidor de descoberta é testado; a entrega do broadcast depende de rede real.
² a geração e a leitura do formato são testadas; a câmera exige aparelho.

## Controle

| Recurso | Windows | Android | Testado | Estado |
|---|---|---|---|---|
| Mover ponteiro (com fração acumulada) | ✅ | ✅ | ✅ | pronto |
| Clique esquerdo, direito, meio | ✅ | ✅ | ✅ | pronto |
| Pressionar/soltar (arraste) | ✅ | ✅ | ✅ | pronto |
| Duplo clique | ✅ | ✅ | ✅ | pronto |
| Rolagem vertical e horizontal | ✅ | ✅ | ✅ | pronto |
| Gestos: 1, 2 e 3 dedos | — | ✅ | manual | pronto |
| Toque longo para arrastar | — | ✅ | manual | pronto |
| Sensibilidade, aceleração, inverter rolagem, vibração | — | ✅ | — | pronto |
| Texto Unicode (acentos, ç) | ✅ | ✅ | ✅ | pronto |
| Modificadores Ctrl/Alt/Shift/Win | ✅ | ✅ | ✅ | pronto |
| F1–F12, setas, navegação | ✅ | ✅ | ✅ | pronto |
| Atalhos prontos e combinações livres | ✅ | ✅ | ✅ | pronto |
| Posição absoluta do ponteiro | ✅ | — | ✅ | pronto (sem UI ainda) |

## Recursos

| Recurso | Windows | Android | Testado | Estado |
|---|---|---|---|---|
| Mídia e volume | ✅ | ✅ | ✅ | pronto |
| Energia (bloquear→desligar) com confirmação | ✅ | ✅ | ✅ | pronto |
| Abrir programas do Menu Iniciar | ✅ | ✅ | ✅ | pronto |
| Área de transferência nos dois sentidos | ✅ | ✅ | ✅ | pronto |
| Explorador de arquivos com sandbox | ✅ | ✅ | ✅ | pronto |
| Download PC → celular com retomada | ✅ | ✅ | ✅ | pronto |
| Upload celular → PC (protocolo) | ✅ | — | ✅ | servidor pronto, falta UI |
| Ver a tela do PC (JPEG adaptativo) | ✅ | ✅ | manual | pronto |
| Qualidade e FPS ajustáveis | ✅ | ✅ | — | pronto |

## Windows

| Recurso | Estado | Testado |
|---|---|---|
| Janela responsiva, cabe em qualquer tela | pronto | manual |
| Fechar no X → bandeja / encerrar / perguntar | pronto | manual |
| Menu completo na bandeja | pronto | manual |
| Iniciar com o Windows | pronto | manual |
| Abrir minimizado | pronto | manual |
| Instância única | pronto | manual |
| Regra de firewall automática (perfil privado) | pronto | manual |
| Teste de porta | pronto | manual |
| Trocar a porta de controle | pronto | ✅ |
| Ligar / pausar / reiniciar o servidor | pronto | ✅ |
| Dispositivos: renomear, bloquear, remover | pronto | ✅ |
| Perguntar antes de aceitar aparelho novo | pronto | ✅ |
| Diagnóstico com exportação | pronto | ✅ |
| Relatório de erro em vez de fechar sozinho | pronto | manual |
| QR de pareamento | pronto | manual |

---

## Não implementado nesta versão

Sem meia-verdade: os itens abaixo **não existem** e não têm botão fingindo que
existem.

| Recurso | Por quê |
|---|---|
| Gamepad virtual e editor de layouts | exige driver de terceiros (ViGEm) instalado no PC; ficaria como botão inerte |
| Sensores (giroscópio, acelerômetro) | só fazem sentido junto do gamepad |
| Celular como webcam / câmera virtual | exige driver de câmera virtual no Windows |
| Câmera do PC no celular | depende do módulo de câmera, não iniciado |
| Monitor virtual / segunda tela | exige driver IddCx assinado |
| Espelhar a tela do Android no PC | MediaProjection, não iniciado |
| Gerenciador de tarefas remoto | não iniciado |
| Modo apresentação dedicado | dá para usar os atalhos (F5, setas) pelo teclado remoto |
| Controle do ponteiro pela imagem da tela remota | a imagem é só visualização; o ponteiro vai pelo touchpad |
| Criptografia fim a fim do tráfego | ver a seção de limitações em `docs/SECURITY.md` |
| Instalador MSI/MSIX | hoje é executável único + `.zip` portátil |
| Tema claro | só escuro |
| Tradução para inglês e espanhol | strings prontas para extrair, tradução não feita |
