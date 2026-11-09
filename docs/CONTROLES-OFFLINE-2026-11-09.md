# Atualização de controles — desenvolvimento offline (09/11/2026)

Esta branch atualiza **PCFlow**, que é um projeto relacionado, mas **não é o mesmo código-fonte do Flow Tutum Next 2.0.0-alpha.10-AS** instalado no Windows.

O computador estava desligado no momento da implementação. Nenhum processo, tarefa agendada, usuário Windows ou configuração da sessão Lil foi alterado.

## Implementado nesta branch

- Android: touchpad dedicado com botões esquerdo, meio e direito, duplo clique, rolagem vertical e horizontal, ajuste de sensibilidade e opção de segurar/soltar para arrastar.
- Android: botão na aba touchpad que abre a aba de teclado e solicita o teclado nativo Android.
- Android: envio de texto **após confirmação do método de entrada (IME)**, com tratamento de substituições e Backspace e fila de mensagens para preservar a ordem.
- Android: conecta diretamente sem exibir novamente o PIN **apenas quando já existe token pareado e válido**, preservando a segurança do PCFlow original.
- Windows: protocolo de botão pressionado/solto e rolagem horizontal, soltando os botões mantidos pressionados ao perder a conexão.
- Windows: botão visível **Desligar servidor e sair**, com confirmação. Não adiciona tarefas agendadas nem watchdog.
- CI: tentativa de correção do ambiente Android do GitHub Actions com SDK 35.

## Importante

- Este repositório ainda usa o protocolo PCFlow TCP v1 e as portas **45455/45456**, diferentes do Flow Tutum Next na porta **49900**.
- A opção de entrar sem PIN por estar simplesmente no mesmo Wi-Fi **não foi habilitada** aqui. O uso de token pareado evita liberar um PC a visitantes da mesma rede.
- Este projeto não transmite a tela do computador. O ajuste de vídeo/zoom do Flow Tutum Next é uma implementação separada e não está presente no PCFlow.
- Um build do PCFlow não substitui o APK `br.flowtutum` e não deve ser instalado esperando atualizar esse aplicativo.

## Testes pendentes com dispositivos reais

1. Compilar Windows e Android no GitHub Actions.
2. Instalar somente quando desejado em ambiente de teste, sem substituir o Flow Tutum Next da AS.
3. Validar digitação com Gboard: letras, acentos, correção automática e exclusões.
4. Testar mouse: esquerda, meio, direita, arraste mantido, rolagem em ambas as direções.
5. Interromper o Wi-Fi durante o arraste e confirmar que o Windows solta o botão.
6. Confirmar que **Desligar servidor e sair** realmente encerra o PCFlow e não cria inicialização automática.

## Integração futura com Flow Tutum Next

Para levar essas mudanças à versão que está instalada na conta AS, é necessário recuperar seus fontes de `C:\Users\AS\Documents\Ander Flow\Flow Tutum\reformulacao-2.0\` quando o PC estiver online, portar os recursos equivalentes e recompilar os executáveis/ o APK com a assinatura original.
