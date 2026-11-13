# Flow Tutum Next — núcleo full stack

> **Produto único:** Windows **AS** + Android `br.flowtutum`. Esta pasta é a base de evolução do **Flow Tutum Next**, **não** o antigo PCFlow. Nenhum processo é iniciado automaticamente por estes arquivos.

## Situação real

- Versão instalada anteriormente na conta AS: **2.0.0-alpha.10**, servidor `http://192.168.1.3:49900`.
- APK **já assinado**: [release do Flow Tutum Next](https://github.com/AnderHonorato/Conectar-PCFlow/releases/tag/flow-tutum-next-v2.0.0-alpha.10-as). Cópia também está no Drive.
- **Esta branch ainda não substitui o executável Windows nem o APK**: o código-fonte da versão instalada está em `C:\Users\AS\Documents\Ander Flow\Flow Tutum\reformulacao-2.0\` e o computador estava desligado na elaboração.
- A conta Windows **Lil não pode ser modificada**. Não instalar serviços, tarefas agendadas, inicializadores de PowerShell ou reinício automático.

## Frontend

| Arquivo | Responsabilidade |
| --- | --- |
| `web/geometria.mjs` | Enquadramento de vídeo, ajustar/preencher, pinça, pan, conversão de coordenadas normalizadas |
| `web/entrada.mjs` | Contrato de mouse, rolagem vertical/horizontal, edição Unicode/IME, controle de botões pressionados |
| `web/painel.html` | Interface mobile-first com abas Tela e Touchpad, teclado, preferências e status real |
| `web/painel.css` | Estilo editorial 2D, orientações vertical e horizontal, tema claro/escuro, redução de movimento |
| `web/painel.mjs` | Gestos, escala, teclado do Android, arraste e adaptador da sessão autenticada |

O painel não finge estar transmitindo vídeo: quando está desconectado informa **Vídeo indisponível**. O desenho real de quadros precisa ser conectado ao renderizador existente na conta AS.

O adaptador usa `window.FlowTutumNext.state` e `window.FlowTutumNext.wsSend()` da aplicação original. Sem ponte autenticada, nenhum comando é enviado.

## Backend

| Arquivo | Responsabilidade |
| --- | --- |
| `server/NucleoAcessoLocal.cs` | Restrição de sub-rede e interface, `Origin`, credenciais com hash SHA-256, expiração e rate limit |
| `server/ServicoDeAcessoLocal.cs` | Fluxo de autenticação local e revogação imediata de sessões |

**Importante:** o modo de acesso sem PIN libera clientes da **mesma sub-rede** quando ativado. O vínculo temporário por IP reduz riscos, mas **não identifica o proprietário do celular**. Em redes com visitantes, use uma rede Wi-Fi dedicada ou desative o acesso direto no menu da bandeja Windows. Não habilitar o modo em redes públicas.

A autenticação, os limites e os estados são implementados como classes testáveis e ainda precisam ser integrados aos endpoints HTTP/WebSocket do executável original.

## Contrato da integração

1. Recuperar o projeto Next da conta AS, mantendo backup antes de substituir qualquer arquivo.
2. Comparar `web/painel.*` ao frontend original e portar o que for compatível, preservando a transmissão H.264/JPEG.
3. Integrar `ServicoDeAcessoLocal` ao `POST /api/local-connect` original e exigir `PodeControlar` no comando WebSocket, não apenas na página de login.
4. Garantir que **desativar Wi-Fi direto** revogue credenciais e libere imediatamente botões de mouse pressionados.
5. Confirmar que o APK Android abre o **mesmo** servidor da AS na porta **49900**. Preservar `applicationId`, certificado de assinatura e dados atuais.
6. Testar com Galaxy A35 em vertical e horizontal no monitor ultrawide 3440×1440, inclusive perda de Wi-Fi durante arraste.
7. Compilar, validar APK assinado e publicar **um único** APK no GitHub e no Drive.

Não mesclar a PR do PCFlow antigo como aplicativo alternativo.

## Validação automatizada

```bash
node --test flow-tutum-next/tests/*.test.mjs
dotnet run --configuration Release --project flow-tutum-next/server/tests/NucleoAcessoLocal.Tests.csproj
```

O GitHub Actions executa estas verificações e uma auditoria de comandos perigosos. Esses testes são **unitários**, não comprovam transmissão de tela real nem uso físico do Galaxy A35.

### Próximo marco de qualidade

- Captura de vídeo e reconexão com cancelamento explícito.
- Teste end-to-end do controle no Windows **AS**, não na Lil.
- Verificação de firewall apenas para perfil privado, nunca liberação indiscriminada para a internet.
- Assinatura e atualização do mesmo APK sem desinstalar nem perder dados.

Registro de unificação: [issue #3](https://github.com/AnderHonorato/Conectar-PCFlow/issues/3).
