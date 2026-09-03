# Testes

## Resumo honesto

| Camada | Quantidade | Executado | Onde |
|---|---|---|---|
| Núcleo .NET (unidade) | 33 | ✅ sim | Linux e Windows |
| Integração Windows ↔ cliente do protocolo | 40 | ✅ sim | Linux e Windows |
| Kotlin (unidade + interoperabilidade) | 10 | ✅ sim | JVM |
| Compilação do WPF | ✅ | ✅ sim | Linux (`EnableWindowsTargeting`) e Windows (CI) |
| APK release assinado | ✅ | ✅ sim | build local + CI |
| Interface Android em aparelho | ❌ | **não** | sem KVM no ambiente |
| Aplicativo Windows em execução | ❌ | **não** | sem host Windows no ambiente |

**Total automatizado: 83 testes, 83 passando.**

O que isso quer dizer sem enrolação: **o protocolo, o servidor, o pareamento, a
segurança e a serialização estão verificados de verdade, com sockets reais.** A
camada que toca o hardware — `SendInput`, captura de tela, bandeja, gestos na
tela do celular — foi escrita com cuidado e compila, mas só a sua primeira
execução em máquina real confirma.

---

## Como rodar

### Núcleo e integração (.NET)

```bash
dotnet test windows/PCFlow.Core.Tests/PCFlow.Core.Tests.csproj
```

Roda em Linux, macOS e Windows: o projeto `PCFlow.Core` não depende de Windows.
As implementações de plataforma são injetadas por interface, e os testes usam
gravadores que registram o que teria sido enviado ao sistema.

### Android

```bash
cd android && gradle :app:testDebugUnitTest
```

### Tudo, do zero

```bash
dotnet clean PCFlow.sln && dotnet test windows/PCFlow.Core.Tests/PCFlow.Core.Tests.csproj
cd android && gradle clean testDebugUnitTest lintDebug assembleRelease
```

---

## O que os testes de integração provam

Não são simulações: cada um sobe um `ServidorPcFlow` real numa porta livre e fala
com ele por TCP, exatamente como o app Android fala.

**Pareamento e sessão**
- PIN correto devolve token, nome da máquina e versão do protocolo
- PIN errado é recusado com mensagem clara e não registra o dispositivo
- PIN é rotacionado após o pareamento e o antigo deixa de valer
- Reconexão com token não pede PIN
- Token falsificado é recusado com código `naoautorizado`
- Dispositivo bloqueado no PC não conecta
- Versão de protocolo incompatível é avisada em vez de falhar de forma obscura

**Comandos**
- Mouse, teclado, atalhos, rolagem e mídia chegam com os valores exatos
- 400 movimentos seguidos chegam na ordem, sem perda e sem duplicação
- Área de transferência funciona nos dois sentidos
- Energia e arquivos desativados nos ajustes são recusados com aviso
- Servidor pausado ignora comandos mas continua respondendo ao heartbeat

**Resiliência**
- 100 ciclos de conectar/desconectar sem deixar o servidor em estado inválido
- Dois celulares ao mesmo tempo, cada um com sua identidade
- 30 sessões seguidas com tráfego não deixam sessões órfãs
- Parar e iniciar cinco vezes seguidas continua funcionando
- Reiniciar o servidor preserva os dispositivos autorizados

**Segurança**
- JSON malformado, vazio ou de tipo errado não derruba a sessão
- Comando desconhecido é ignorado
- Mensagem acima de 256 KB encerra só aquela sessão
- Comando antes do handshake não é executado
- Força bruta de PIN é bloqueada por origem
- Path traversal recusado na leitura e na gravação de arquivos
- Remover um dispositivo derruba a sessão dele na hora

**Interoperabilidade entre os dois projetos**
- `tests/interop/handshake-android.json` é gerado pelo código Kotlin e conferido
  por um teste Kotlin; o mesmo arquivo é enviado cru ao servidor C# por um teste
  C#, que confirma o pareamento. Se um lado mudar o formato sem o outro, quebra.

---

## Roteiro de verificação manual

Isto é o que **você** precisa rodar na primeira instalação, porque nenhuma
máquina aqui podia fazer.

### No Windows

1. Abrir o `PCFlow.exe`. A janela precisa caber na tela e ser redimensionável
   pelas bordas. *(defeito #1)*
2. Arrastar a borda até o mínimo e até a tela cheia.
3. Abrir uma segunda cópia: deve avisar que já está aberto, sem travar. *(#2)*
4. Fechar no X: deve ir para a bandeja e continuar servindo. Trocar em
   **Ajustes → Ao fechar** e confirmar os três comportamentos.
5. Percorrer as cinco páginas: nenhum botão pode ser inerte. *(#16)*
6. **Conexão → Liberar no firewall**, aceitar o UAC, depois **Testar porta**.
7. **Diagnóstico → Exportar diagnóstico** e abrir o arquivo gerado.
8. Menu da bandeja: Abrir, Dispositivos, Configurações, Pausar, Reiniciar, Sair.

### No Android

9. Instalar o APK e abrir. Cabeçalho não pode ficar sob a barra de status. *(#19)*
10. Conferir o IP do celular mostrado na tela inicial contra o IP do PC. *(mesma rede)*
11. O PC deve aparecer sozinho na lista em poucos segundos. *(#4)*
12. **Escanear QR** e confirmar a autorização no PC.
13. Touchpad: mover devagar (o ponteiro tem que acompanhar — *#7*), toque,
    toque com 2 dedos (menu de contexto), 3 dedos (botão do meio), 2 dedos
    deslizando (rolagem), toque longo e arrastar. *(#17)*
14. Teclado: digitar acentuação, `ç`, Ctrl+C/Ctrl+V, F5, setas.
15. Mídia com algo tocando; volume; bloquear o PC.
16. Arquivos: navegar e baixar um arquivo.
17. Tela: ver a imagem do PC.

### Resiliência

18. Desligar o Wi‑Fi do celular por 10 s e religar: reconecta sozinho. *(#9, #10)*
19. Fechar o app e reabrir: reconecta sem pedir PIN. *(#11)*
20. Reiniciar o roteador; esperar; conferir a reconexão.
21. Bloquear o celular por 5 minutos com a sessão aberta; desbloquear. *(#18)*
22. No PC, **Dispositivos → Remover**; no celular a sessão cai e o próximo
    acesso pede PIN.

Se algo aqui falhar, o diagnóstico exportado no Windows tem o registro do que
aconteceu — mande o arquivo que dá para achar a causa rápido.
