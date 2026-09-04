# Servidor de retransmissão do PCFlow

Este é o servidor que você vai hospedar quando quiser conectar de qualquer
lugar sem depender do roteador. Ele resolve o caso em que a operadora usa
CGNAT e não existe porta para abrir.

## O que ele faz (e o que ele não vê)

O servidor é um encanador cego. Ele guarda quais computadores estão online,
recebe o pedido do celular e emenda os dois sockets. Só isso.

O PCFlow do PC e o aplicativo continuam falando **TLS de ponta a ponta com
pinagem de certificado**: a chave privada nunca sai do seu computador e a
identidade dele viaja dentro do código de acesso. Por isso o servidor não
enxerga a sua tela, o que você digita nem os arquivos que passam — mesmo que
alguém invada a máquina onde ele roda.

Consumo: só CPU de cópia de bytes e a banda das sessões. Uma VPS de 1 vCPU e
1 GB dá conta de várias sessões simultâneas.

## Requisitos

- .NET 8 (runtime basta) ou o publish autocontido
- Uma porta TCP aberta na internet — padrão **45460**
- Linux, Windows ou macOS

## Rodando

```bash
# a partir do repositório
dotnet run --project server/PCFlow.Relay -- 45460

# ou publicado
dotnet publish server/PCFlow.Relay -c Release -o /opt/pcflow-relay
/opt/pcflow-relay/pcflow-relay 45460
```

A porta também pode vir do ambiente:

```bash
PCFLOW_RELAY_PORTA=45460 /opt/pcflow-relay/pcflow-relay
```

Se a máquina não tiver IPv6, ele escuta só em IPv4 sozinho, sem falhar.

## Como serviço no Linux (systemd)

`/etc/systemd/system/pcflow-relay.service`:

```ini
[Unit]
Description=PCFlow Relay
After=network.target

[Service]
ExecStart=/opt/pcflow-relay/pcflow-relay 45460
Restart=always
RestartSec=3
User=pcflow
Environment=DOTNET_EnableDiagnostics=0

[Install]
WantedBy=multi-user.target
```

```bash
sudo useradd --system --no-create-home pcflow
sudo systemctl daemon-reload
sudo systemctl enable --now pcflow-relay
sudo ufw allow 45460/tcp        # ou a regra equivalente do seu provedor
```

## Ligando o PC nele

1. No PCFlow do Windows, abra **Pela internet**.
2. Em *Servidor de retransmissão*, escreva o endereço (`meuservidor.com` ou
   `meuservidor.com:45460`) e clique em **Salvar e conectar ao servidor**.
3. A tela passa a mostrar o código de acesso do servidor. Copie.
4. Marque **Aceitar conexões de fora da rede local** e defina uma senha em
   **Segurança** — sem senha, nenhuma conexão externa é aceita.

## Ligando o celular

1. No aplicativo, toque em **Conectar por código (fora do Wi-Fi)**.
2. Cole o código, informe a senha de acesso do PC e escreva o endereço do
   servidor no campo *Servidor de retransmissão*.
3. O endereço fica salvo para as próximas vezes.

## Protocolo (para quem for mexer no código)

Tudo é JSON por linha, só até o canal ficar pronto; depois são bytes crus.

```
PC     -> {"tipo":"registrar","codigo":"123456789","segredo":"…","nome":"PC-DA-SALA"}
PC     <- {"tipo":"registrado","codigo":"123456789"}
Cel    -> {"tipo":"conectar","codigo":"123456789","alvo":"controle"}
PC     <- {"tipo":"chamada","canal":"ab12…","alvo":"controle"}
PC     -> (nova conexão) {"tipo":"canal","canal":"ab12…","segredo":"…"}
ambos  <- {"tipo":"pronto"}
… daqui em diante o servidor só copia bytes de um lado para o outro
```

O `segredo` é sorteado uma vez pelo PC e guardado na configuração dele. É o que
impede outra pessoa de registrar o mesmo código e sequestrar as conexões: um
registro com segredo diferente é recusado enquanto o dono estiver online.

Canais que ninguém veio buscar são descartados em 30 segundos.

## Testes

O servidor é testado com o binário de verdade, subindo o processo e passando
bytes pelos dois lados:

```bash
dotnet test windows/PCFlow.Tests/PCFlow.Tests.csproj
```
