# PCFlow

Controle o seu PC com Windows pelo celular Android, dentro da rede local.
Sem conta, sem nuvem, sem anúncios, sem assinatura.

> **Requisito principal:** o computador e o celular precisam estar na **mesma rede
> local** (mesmo roteador). Redes de convidados, VPN ligada no celular e Wi‑Fi de
> 5 GHz separado do cabo do PC são as causas mais comuns de "não conecta". O app
> mostra o IP do celular na tela inicial justamente para você conferir isso.

---

## O que já funciona

| Recurso | Windows | Android |
|---|---|---|
| Descoberta automática na LAN (UDP) | ✅ | ✅ |
| Pareamento por QR Code | ✅ gera | ✅ escaneia |
| Pareamento por PIN de 6 dígitos | ✅ | ✅ |
| Endereço manual (quando a rede bloqueia broadcast) | ✅ mostra | ✅ digita |
| Reconexão automática (troca de IP, queda de Wi‑Fi) | ✅ | ✅ |
| Heartbeat com latência em tempo real | ✅ | ✅ |
| Touchpad: mover, clicar, arrastar, rolar, 3 botões | — | ✅ |
| Teclado completo: texto Unicode, F1–F12, setas, modificadores | ✅ | ✅ |
| Atalhos prontos (Ctrl+C, Alt+Tab, Win+D…) | ✅ | ✅ |
| Controle de mídia e volume | ✅ | ✅ |
| Energia: bloquear, suspender, hibernar, reiniciar, desligar | ✅ | ✅ |
| Abrir programas do Menu Iniciar | ✅ | ✅ |
| Área de transferência compartilhada nos dois sentidos | ✅ | ✅ |
| Explorador de arquivos + download para o celular | ✅ | ✅ |
| Ver a tela do PC no celular (JPEG adaptativo) | ✅ | ✅ |
| Fechar a janela minimiza para a bandeja (configurável) | ✅ | — |
| Iniciar com o Windows | ✅ | — |
| Regra de firewall automática | ✅ | — |
| Gerenciar dispositivos: renomear, bloquear, remover | ✅ | — |
| Diagnóstico exportável | ✅ | — |

O que **não** está implementado nesta versão está listado sem enfeite em
[`FEATURE_MATRIX.md`](FEATURE_MATRIX.md) — nada de botão que não faz nada.

---

## Instalação rápida

### 1. No computador

Baixe `PCFlow.exe` (ou o `.zip` portátil) e execute.
Na primeira vez, vá em **Conexão → Liberar no firewall** e aceite o pedido de
administrador. É o passo que evita 90% das falhas de conexão.

### 2. No celular

Instale `PCFlow-Android-v1.0.0.apk`. O Android vai pedir para permitir
"instalar de fontes desconhecidas" — é normal para APK fora da Play Store.

### 3. Parear

1. Abra o PCFlow no PC. Ele mostra o nome do computador, o IP e um QR Code.
2. Abra o app no celular. O PC aparece sozinho na lista.
3. Toque em **Escanear QR** (ou toque no PC da lista e digite o PIN).
4. Confirme no PC quando ele perguntar se pode autorizar o celular.

A partir daí é só abrir o app: ele reconecta sozinho, sem PIN.

---

## Quando não conectar

Na ordem, é quase sempre um destes:

1. **Redes diferentes.** Compare o IP mostrado no app com o IP mostrado no PC.
   Os três primeiros números precisam bater (`192.168.0.x` dos dois lados).
2. **Firewall.** No PC: **Conexão → Liberar no firewall**. Depois **Testar porta**.
3. **Broadcast bloqueado pelo roteador.** O PC não aparece na lista, mas o
   endereço direto funciona: use **Digitar IP** no app com o IP mostrado no PC.
4. **Servidor pausado ou parado.** A barra de status no topo do PCFlow do Windows
   diz o estado atual.

A tela **Diagnóstico** no Windows registra tudo (conexões, autenticação, erros) e
exporta um arquivo `.txt` para você conferir.

---

## Estrutura do repositório

```
PCFlow.sln
windows/
  PCFlow.Core/         núcleo sem dependência de Windows: protocolo, servidor,
                       pareamento, arquivos, descoberta — é o que os testes cobrem
  PCFlow.Windows/      interface WPF, bandeja, SendInput, captura de tela, firewall
  PCFlow.Core.Tests/   75 testes unitários e de integração (rodam em Linux e Windows)
android/
  app/                 aplicativo Kotlin + Jetpack Compose
tests/interop/         handshake compartilhado, verificado pelos dois lados
docs/                  arquitetura, protocolo, segurança, build e testes
```

---

## Documentação

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — como as peças se encaixam
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — o protocolo, mensagem por mensagem
- [`docs/SECURITY.md`](docs/SECURITY.md) — pareamento, tokens, limites, o que é registrado
- [`docs/BUILD.md`](docs/BUILD.md) — compilar do zero, assinar o APK
- [`docs/TESTS.md`](docs/TESTS.md) — o que é testado e o que não é
- [`FEATURE_MATRIX.md`](FEATURE_MATRIX.md) — estado real de cada recurso
- [`BUGS.md`](BUGS.md) — os defeitos encontrados e como foram corrigidos
- [`CHANGELOG.md`](CHANGELOG.md)

## Licença e origem

Implementação própria. Inspirada em funcionalidades públicas de aplicativos do
gênero, sem reaproveitar código, marca, ícones ou protocolo de terceiros.
