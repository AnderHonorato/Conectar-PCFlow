# PCFlow V1.3

Versão nova e isolada do PCFlow, criada dentro da pasta `PCFlow-v1.3.0/`. Os arquivos existentes na raiz do repositório não são substituídos por esta entrega.

## Destaques

### Android
- nova interface nativa Kotlin + Jetpack Compose;
- conexão por ID, descoberta LAN e QR;
- sessão remota em tela cheia;
- modos **Toque**, **Touchpad** e **Visualizar**;
- clique simples, duplo clique, clique direito e arrastar;
- mapeamento de toque considerando proporção/letterbox da tela remota;
- feedback visual animado exatamente no ponto tocado;
- zoom e pan locais no modo Visualizar;
- menu flutuante animado para teclado, monitor, clipboard e ferramentas;
- acesso ao painel completo anterior para preservar recursos já existentes.

### Windows
- painel WPF redesenhado com hierarquia visual mais clara;
- identidade PCFlow em grafite/preto, dourado e turquesa;
- cartões para endereço, desktop remoto, diagnóstico e dispositivos;
- estados e permissões reorganizados;
- feedback de hover/press nos botões;
- transporte e funções do host preservados da base segura selecionada.

## Compilar

### Windows
```powershell
dotnet restore PCFlow.sln
dotnet build PCFlow.sln -c Release
dotnet publish windows/PCFlow.Windows/PCFlow.Windows.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

### Android
```bash
gradle -p android :app:testDebugUnitTest
gradle -p android :app:assembleDebug
```

## Observação de segurança

A conexão remota deve ser usada apenas em dispositivos próprios ou com autorização explícita. O ID do PC não autentica sozinho; aprovação local, pareamento ou senha configurada continuam sendo exigidos conforme o modo de acesso.
