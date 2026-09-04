using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace PCFlow.Windows.Core;

public sealed class ServidorArquivosPcFlow : IAsyncDisposable
{
    public const int Porta = 45458;
    private readonly CancellationTokenSource _cts = new();
    private readonly ArmazenamentoConfiguracao _armazenamento = new();
    private readonly TlsIdentidade _tls = new();
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    private TcpListener? _listener;
    private Task? _tarefa;

    public event Action<string>? Status;

    public Task IniciarAsync()
    {
        if (_listener is not null) return Task.CompletedTask;
        _listener = new TcpListener(IPAddress.Any, Porta);
        _listener.Start();
        _tarefa = AceitarAsync(_cts.Token);
        return Task.CompletedTask;
    }

    private async Task AceitarAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _listener is not null)
        {
            try
            {
                var cliente = await _listener.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => AtenderAsync(cliente, ct), ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { Status?.Invoke($"Arquivos: {ex.Message}"); }
        }
    }

    private async Task AtenderAsync(TcpClient cliente, CancellationToken ct)
    {
        using (cliente)
        {
            var remoto = (cliente.Client.RemoteEndPoint as IPEndPoint)?.Address;
            if (remoto is null || !EhRedeLocal(remoto)) return;
            cliente.NoDelay = true;

            using var ssl = new SslStream(cliente.GetStream(), false);
            try
            {
                // Mesma correção do canal de controle: fixar Tls12|Tls13 quebrava
                // o handshake no Windows 10, cujo SChannel não expõe TLS 1.3.
                await TlsPcFlow.AutenticarServidorAsync(ssl, _tls.Certificado, ct);

                var autenticacaoTexto = await LerLinhaAsync(ssl, ct);
                if (autenticacaoTexto is null) return;
                using var authDoc = JsonDocument.Parse(autenticacaoTexto);
                var auth = authDoc.RootElement;
                if (auth.GetProperty("tipo").GetString() != "arquivos_ola") return;

                var dispositivoId = auth.TryGetProperty("dispositivoId", out var idEl) ? idEl.GetString() : null;
                var token = auth.TryGetProperty("token", out var tokenEl) ? tokenEl.GetString() : null;
                var maquinaId = auth.TryGetProperty("maquinaId", out var maquinaEl) ? maquinaEl.GetString() : null;
                var configuracao = _armazenamento.Carregar();

                if (!configuracao.PermitirArquivos || string.IsNullOrWhiteSpace(dispositivoId) || maquinaId != configuracao.MaquinaId)
                {
                    await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Acesso a arquivos não permitido" }, ct);
                    return;
                }

                var conhecido = configuracao.Dispositivos.FirstOrDefault(d => d.Id == dispositivoId && !d.Bloqueado);
                if (conhecido is null || !TokenIgual(conhecido.Token, token))
                {
                    await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Dispositivo não autorizado" }, ct);
                    return;
                }

                await EscreverJsonAsync(ssl, new { tipo = "ok" }, ct);
                var comandoTexto = await LerLinhaAsync(ssl, ct);
                if (comandoTexto is null) return;
                using var comandoDoc = JsonDocument.Parse(comandoTexto);
                await ProcessarAsync(ssl, comandoDoc.RootElement, ct);
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (Exception ex)
            {
                try { await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = ex.Message }, CancellationToken.None); } catch { }
            }
        }
    }

    private async Task ProcessarAsync(SslStream ssl, JsonElement comando, CancellationToken ct)
    {
        var tipo = comando.TryGetProperty("tipo", out var tipoEl) ? tipoEl.GetString() : "";
        var caminho = comando.TryGetProperty("caminho", out var caminhoEl) ? caminhoEl.GetString() ?? "" : "";

        switch (tipo)
        {
            case "listar":
                await ListarAsync(ssl, caminho, ct);
                break;
            case "baixar":
                await BaixarAsync(ssl, caminho, ct);
                break;
            case "enviar":
                var nome = comando.TryGetProperty("nome", out var nomeEl) ? nomeEl.GetString() ?? "arquivo" : "arquivo";
                var tamanho = comando.TryGetProperty("tamanho", out var tamanhoEl) ? tamanhoEl.GetInt64() : -1;
                await ReceberAsync(ssl, caminho, nome, tamanho, ct);
                break;
            case "mkdir":
                Directory.CreateDirectory(caminho);
                await EscreverJsonAsync(ssl, new { tipo = "ok" }, ct);
                break;
            case "apagar":
                if (Directory.Exists(caminho)) Directory.Delete(caminho, true);
                else if (File.Exists(caminho)) File.Delete(caminho);
                await EscreverJsonAsync(ssl, new { tipo = "ok" }, ct);
                break;
            case "renomear":
                var novoNome = comando.TryGetProperty("novoNome", out var novoEl) ? novoEl.GetString() : null;
                if (string.IsNullOrWhiteSpace(novoNome) || novoNome.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0) throw new InvalidDataException("Nome inválido");
                var pai = Path.GetDirectoryName(caminho) ?? throw new InvalidDataException("Caminho inválido");
                var destino = Path.Combine(pai, novoNome);
                if (Directory.Exists(caminho)) Directory.Move(caminho, destino);
                else File.Move(caminho, destino);
                await EscreverJsonAsync(ssl, new { tipo = "ok" }, ct);
                break;
            default:
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Comando desconhecido" }, ct);
                break;
        }
    }

    private async Task ListarAsync(SslStream ssl, string caminho, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(caminho))
        {
            var raizes = DriveInfo.GetDrives().Where(d => d.IsReady).Select(d => new
            {
                nome = string.IsNullOrWhiteSpace(d.VolumeLabel) ? d.Name : $"{d.Name}  {d.VolumeLabel}",
                caminho = d.RootDirectory.FullName,
                pasta = true,
                tamanho = d.TotalSize,
                modificado = "",
                raiz = true
            }).ToArray();
            await EscreverJsonAsync(ssl, new { tipo = "lista", caminho = "", pai = "", itens = raizes }, ct);
            return;
        }

        var diretorio = new DirectoryInfo(caminho);
        if (!diretorio.Exists) throw new DirectoryNotFoundException("Pasta não encontrada");

        var itens = diretorio.EnumerateFileSystemInfos()
            .Where(i => !i.Attributes.HasFlag(FileAttributes.System))
            .OrderByDescending(i => i is DirectoryInfo)
            .ThenBy(i => i.Name, StringComparer.CurrentCultureIgnoreCase)
            .Take(2000)
            .Select(i => new
            {
                nome = i.Name,
                caminho = i.FullName,
                pasta = i is DirectoryInfo,
                tamanho = i is FileInfo f ? f.Length : 0L,
                modificado = i.LastWriteTimeUtc.ToString("O"),
                raiz = false
            }).ToArray();

        var pai = diretorio.Parent?.FullName ?? "";
        await EscreverJsonAsync(ssl, new { tipo = "lista", caminho = diretorio.FullName, pai, itens }, ct);
    }

    private async Task BaixarAsync(SslStream ssl, string caminho, CancellationToken ct)
    {
        var arquivo = new FileInfo(caminho);
        if (!arquivo.Exists) throw new FileNotFoundException("Arquivo não encontrado");
        await EscreverJsonAsync(ssl, new { tipo = "arquivo", nome = arquivo.Name, tamanho = arquivo.Length }, ct);
        await using var input = new FileStream(arquivo.FullName, FileMode.Open, FileAccess.Read, FileShare.ReadWrite, 128 * 1024, true);
        await input.CopyToAsync(ssl, 128 * 1024, ct);
        await ssl.FlushAsync(ct);
    }

    private async Task ReceberAsync(SslStream ssl, string pastaDestino, string nome, long tamanho, CancellationToken ct)
    {
        if (tamanho < 0 || tamanho > 512L * 1024 * 1024 * 1024) throw new InvalidDataException("Tamanho inválido");
        if (nome.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0) throw new InvalidDataException("Nome inválido");
        if (!Directory.Exists(pastaDestino)) throw new DirectoryNotFoundException("Pasta de destino não encontrada");

        var destino = Path.Combine(pastaDestino, nome);
        var temporario = destino + ".pcflow-part";
        await using (var output = new FileStream(temporario, FileMode.Create, FileAccess.Write, FileShare.None, 128 * 1024, true))
        {
            var restante = tamanho;
            var buffer = new byte[128 * 1024];
            while (restante > 0)
            {
                var ler = (int)Math.Min(buffer.Length, restante);
                var lidos = await ssl.ReadAsync(buffer.AsMemory(0, ler), ct);
                if (lidos == 0) throw new EndOfStreamException("Transferência interrompida");
                await output.WriteAsync(buffer.AsMemory(0, lidos), ct);
                restante -= lidos;
            }
        }
        File.Move(temporario, destino, true);
        await EscreverJsonAsync(ssl, new { tipo = "ok", caminho = destino }, ct);
    }

    private async Task EscreverJsonAsync(Stream stream, object valor, CancellationToken ct)
    {
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(valor, _json) + "\n");
        await stream.WriteAsync(bytes, ct);
        await stream.FlushAsync(ct);
    }

    private static async Task<string?> LerLinhaAsync(Stream stream, CancellationToken ct)
    {
        using var ms = new MemoryStream();
        var buffer = new byte[1];
        while (ms.Length < 256 * 1024)
        {
            var lidos = await stream.ReadAsync(buffer, ct);
            if (lidos == 0) return ms.Length == 0 ? null : Encoding.UTF8.GetString(ms.ToArray());
            if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(ms.ToArray());
            if (buffer[0] != (byte)'\r') ms.WriteByte(buffer[0]);
        }
        throw new InvalidDataException("Mensagem muito grande");
    }

    private static bool TokenIgual(string esperado, string? recebido)
    {
        if (string.IsNullOrWhiteSpace(recebido)) return false;
        var a = Encoding.UTF8.GetBytes(esperado);
        var b = Encoding.UTF8.GetBytes(recebido);
        return a.Length == b.Length && CryptographicOperations.FixedTimeEquals(a, b);
    }

    private static bool EhRedeLocal(IPAddress endereco)
    {
        if (IPAddress.IsLoopback(endereco) || endereco.IsIPv6LinkLocal) return true;
        if (endereco.IsIPv4MappedToIPv6) endereco = endereco.MapToIPv4();
        if (endereco.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = endereco.GetAddressBytes();
        return b[0] == 10 || b[0] == 127 || (b[0] == 192 && b[1] == 168) ||
               (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 169 && b[1] == 254);
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        _listener?.Stop();
        if (_tarefa is not null) try { await _tarefa; } catch { }
        _tls.Certificado.Dispose();
        _cts.Dispose();
    }
}
