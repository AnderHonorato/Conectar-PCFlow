PCFlow v1.0.0 — instalação
==========================

IMPORTANTE: o computador e o celular precisam estar na MESMA REDE LOCAL
(mesmo roteador). Rede de convidados, VPN ligada no celular ou o PC numa
faixa de IP diferente impedem a conexão. O app mostra o IP do celular na
tela inicial justamente para você conferir isso na hora.

-------------------------------------------------------------------
1. NO COMPUTADOR
-------------------------------------------------------------------

Opção A — PCFlow-Windows-v1.0.0.exe  (485 KB)
  Precisa do .NET 8 Desktop Runtime instalado.
  Se ao abrir aparecer um aviso pedindo o runtime, baixe em:
  https://dotnet.microsoft.com/download/dotnet/8.0/runtime  (opção
  "Desktop Runtime", x64)

Opção B — PCFlow-Windows-Portable-v1.0.0.zip  (63 MB)
  Não precisa de nada instalado. Descompacte e execute PCFlow.exe.
  Use esta se a opção A reclamar do runtime.

O Windows SmartScreen pode avisar que o programa é desconhecido, porque o
executável não tem assinatura digital comercial. Clique em "Mais informações"
e depois em "Executar assim mesmo".

PRIMEIRO PASSO DEPOIS DE ABRIR:
  Conexão -> Liberar no firewall -> aceite o pedido de administrador.
  É o que evita a maioria das falhas de conexão. Depois use "Testar porta"
  para confirmar.

-------------------------------------------------------------------
2. NO CELULAR
-------------------------------------------------------------------

Instale PCFlow-Android-v1.0.0.apk (Android 8.0 ou superior).
O Android vai pedir permissão para instalar de fonte desconhecida — normal
para APK fora da Play Store.

-------------------------------------------------------------------
3. PAREAR (uma única vez)
-------------------------------------------------------------------

  1. Abra o PCFlow no computador.
  2. Abra o app no celular — o PC aparece sozinho na lista.
  3. Toque em "Escanear QR" e aponte para o código na tela do PC.
     (ou toque no PC da lista e digite o PIN de 6 dígitos)
  4. Confirme no computador quando ele perguntar se autoriza o celular.

Das próximas vezes é só abrir o app: ele reconecta sozinho, sem PIN.

-------------------------------------------------------------------
SE NÃO CONECTAR
-------------------------------------------------------------------

  1. Mesma rede?  Compare o IP mostrado no app com o do PC. Os três
     primeiros números têm que bater (ex.: 192.168.0.x nos dois).
  2. Firewall?    No PC: Conexão -> Liberar no firewall -> Testar porta.
  3. O PC não aparece na lista? Alguns roteadores bloqueiam a busca
     automática. Use "Digitar IP" no app com o endereço mostrado no PC.
  4. Servidor parado? A barra no topo do PCFlow mostra o estado.

A tela Diagnóstico no Windows registra tudo e exporta um arquivo .txt.

-------------------------------------------------------------------
O QUE ESTE APLICATIVO NÃO FAZ (v1.0.0)
-------------------------------------------------------------------

Gamepad virtual, sensores, celular como webcam, monitor virtual e
espelhamento da tela do Android não estão implementados. A lista completa
e honesta está em FEATURE_MATRIX.md no repositório.

Aviso de segurança: o tráfego não é criptografado. A autenticação impede
que outro aparelho controle o PC, mas não use em Wi-Fi público ou aberto.
Detalhes em docs/SECURITY.md.
