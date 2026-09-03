# Protocolo PCFlow v2

Transporte: **TCP**, uma mensagem **JSON por linha** (`\n`), UTF-8.
Descoberta: **UDP broadcast**.

| | Porta | Uso |
|---|---|---|
| Descoberta | 45455/UDP | o celular pergunta, o PC responde |
| Controle | 45456/TCP | sessão autenticada (configurável) |

Limites: 256 KB por linha; 25 s sem mensagem encerra a sessão.
A versão do protocolo é enviada no handshake e a divergência é recusada com
mensagem clara, para nunca falhar de forma obscura entre versões.

---

## 1. Descoberta

Celular → broadcast UDP 45455, para `255.255.255.255` **e** para o endereço de
broadcast de cada interface IPv4 ativa:

```
PCFLOW_DISCOVER_V2
```

PC → resposta unicast ao remetente:

```json
{"tipo":"anuncio","nome":"DESKTOP-ANDER","porta":45456,"protocolo":2,"versao":"1.0.0"}
```

O PC também responde a sondas `PCFLOW_DISCOVER*` de versões anteriores.

---

## 2. Handshake

Primeira linha do celular, obrigatória. Qualquer outra coisa é recusada.

```json
{"tipo":"ola","protocolo":2,"dispositivoId":"uuid","nome":"Galaxy de Anderson",
 "modelo":"Samsung SM-G991B","versao":"1.0.0","token":"...","pin":"482731"}
```

- `dispositivoId` — UUID gerado uma vez por instalação; identifica o aparelho.
- `token` — presente a partir do segundo acesso.
- `pin` — só no pareamento inicial.

Respostas possíveis:

```json
{"tipo":"pareado","token":"<base64 de 32 bytes>","nome":"DESKTOP-ANDER",
 "protocolo":2,"versao":"1.0.0","recursos":{...}}

{"tipo":"conectado","token":"...","nome":"DESKTOP-ANDER","protocolo":2,
 "versao":"1.0.0","recursos":{...}}

{"tipo":"erro","codigo":"pininvalido|bloqueado|recusado|naoautorizado|versao|handshake|fora_da_lan",
 "mensagem":"texto para mostrar ao usuário"}
```

`recursos` diz o que o PC liberou nesta sessão, e o app esconde as abas
correspondentes quando o recurso está desligado:

```json
{"arquivos":true,"tela":true,"energia":true,"areaTransferencia":true,"atalhos":true}
```

---

## 3. Celular → PC

| Tipo | Campos | Efeito |
|---|---|---|
| `ping` | `t` (ms) | devolve `pong` com o mesmo `t` |
| `mouse_move` | `dx`, `dy` (double) | move o ponteiro; a fração é acumulada |
| `mouse_abs` | `x`, `y` (0..1) | posição absoluta na mesa virtual |
| `mouse_click` | `botao`: `left`\|`right`\|`middle`, `acao`: `click`\|`down`\|`up` | botão |
| `scroll` | `dx`, `dy` (múltiplos de 120) | rolagem horizontal e vertical |
| `texto` | `texto` | digita Unicode (acentos e `ç` inclusos) |
| `tecla` | `tecla`, `mods`: `["ctrl","shift","alt","win"]` | tecla com modificadores |
| `atalho` | `acao`: `"ctrl+shift+s"` | combinação em uma string |
| `media` | `acao`: `playpause`, `next`, `previous`, `stop`, `volumeup`, `volumedown`, `mute` | mídia |
| `power` | `acao`: `lock`, `monitoroff`, `sleep`, `hibernate`, `shutdown`, `restart`, `signout` | energia |
| `app_listar` | — | pede a lista de atalhos |
| `app_abrir` | `acao` (id da lista) | abre um item **da lista**, nunca caminho livre |
| `clipboard_enviar` | `texto` | grava na área de transferência do PC |
| `clipboard_pedir` | — | pede o conteúdo atual |
| `arq_listar` | `caminho` (vazio = raízes) | lista pasta autorizada |
| `arq_baixar` | `caminho`, `offset` | pede um bloco de 128 KB |
| `arq_enviar` | `nome`, `offset`, `dados` (base64), `fim` | envia bloco ao PC |
| `tela_iniciar` | `largura`, `qualidade` (20–90), `fps` (5–30) | inicia o envio de quadros |
| `tela_parar` | — | para |
| `desconectar` | — | encerra a sessão |

## 4. PC → celular

| Tipo | Campos |
|---|---|
| `pong` | `t`, `conectados` |
| `aviso` | `mensagem` — recurso desativado ou ação recusada |
| `clipboard` | `texto` |
| `app_lista` | `itens[]` (`nome`, `caminho` = id) |
| `arq_lista` | `caminho`, `itens[]` (`nome`, `caminho`, `pasta`, `tamanho`) |
| `arq_dados` | `caminho`, `offset`, `tamanho`, `dados` (base64), `fim` |
| `arq_ack` | `offset` — próximo offset esperado |
| `arq_recebido` | `caminho`, `ok` |
| `arq_erro` | `mensagem` |
| `tela_quadro` | `dados` (JPEG base64), `largura`, `altura` |
| `erro` | `codigo`, `mensagem` |

---

## 5. Transferência de arquivos

Blocos de 128 KB com offset explícito nas duas direções, o que dá retomada de
graça: basta pedir a partir do offset já recebido. `fim: true` marca o último
bloco.

---

## 6. Compatibilidade

`Protocolo.Versao` existe nos dois lados (`windows/PCFlow.Core/Protocolo.cs` e
`android/.../rede/Protocolo.kt`) e precisa ser subido junto sempre que o formato
mudar de forma incompatível. O arquivo `tests/interop/handshake-android.json`
mantém os dois honestos: um teste de cada lado o valida.
