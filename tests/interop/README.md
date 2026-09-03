# Teste de interoperabilidade Android ↔ Windows

`handshake-android.json` é o handshake exato que o app Android envia, com chaves
ordenadas para virar um arquivo estável.

Dois testes o usam, um de cada lado:

- **Android** (`ProtocoloTest.handshake gerado bate com o arquivo de interoperabilidade`)
  gera o JSON com `Protocolo.ola(...)` e compara com este arquivo.
- **Windows** (`TestesInterop.HandshakeGeradoPeloAndroidEhAceito`) sobe o servidor
  real e envia este arquivo cru como primeira linha da conexão.

Se qualquer um dos lados mudar o formato sem atualizar o outro, um dos dois
testes quebra — que é o objetivo. Ao mudar o protocolo de propósito, regenere o
arquivo e suba a versão em `Protocolo.VERSAO` nos dois projetos.
