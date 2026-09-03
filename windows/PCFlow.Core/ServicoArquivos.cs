namespace PCFlow.Core;

/// <summary>
/// Explorador e transferência de arquivos com sandbox de caminhos.
///
/// Só permite navegar dentro das raízes autorizadas (pastas do usuário por padrão).
/// Qualquer tentativa de "../" ou caminho absoluto fora das raízes é recusada —
/// esse é o teste de path traversal exigido pelo plano de segurança.
/// </summary>
public sealed class ServicoArquivos
{
    private readonly Func<ConfiguracaoPcFlow> _config;

    public ServicoArquivos(Func<ConfiguracaoPcFlow> config) => _config = config;

    public IReadOnlyList<string> Raizes()
    {
        var cfg = _config();
        if (cfg.PastasCompartilhadas.Count > 0)
            return cfg.PastasCompartilhadas.Where(Directory.Exists).ToList();

        var padrao = new[]
        {
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments),
            Environment.GetFolderPath(Environment.SpecialFolder.MyPictures),
            Environment.GetFolderPath(Environment.SpecialFolder.MyVideos),
            Environment.GetFolderPath(Environment.SpecialFolder.MyMusic),
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory)
        };
        return padrao.Where(p => !string.IsNullOrEmpty(p) && Directory.Exists(p)).Distinct().ToList();
    }

    /// <summary>Normaliza e confirma que o caminho está dentro de alguma raiz autorizada.</summary>
    public bool CaminhoPermitido(string? caminho, out string completo)
    {
        completo = "";
        if (string.IsNullOrWhiteSpace(caminho)) return false;
        string alvo;
        try { alvo = Path.GetFullPath(caminho); }
        catch (Exception) { return false; }

        foreach (var raiz in Raizes())
        {
            var r = Path.GetFullPath(raiz).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            if (alvo.Equals(r, StringComparison.OrdinalIgnoreCase) ||
                alvo.StartsWith(r + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
            {
                completo = alvo;
                return true;
            }
        }
        return false;
    }

    public List<ItemArquivo> Listar(string? caminho)
    {
        // Caminho vazio devolve as raízes autorizadas.
        if (string.IsNullOrWhiteSpace(caminho))
        {
            return Raizes().Select(r => new ItemArquivo
            {
                Nome = Path.GetFileName(r.TrimEnd(Path.DirectorySeparatorChar)) is { Length: > 0 } n ? n : r,
                Caminho = r,
                Pasta = true
            }).ToList();
        }

        if (!CaminhoPermitido(caminho, out var pasta) || !Directory.Exists(pasta))
            throw new UnauthorizedAccessException("Pasta fora das áreas autorizadas.");

        var itens = new List<ItemArquivo>();
        foreach (var d in Directory.EnumerateDirectories(pasta))
        {
            if (EhOculto(d)) continue;
            itens.Add(new ItemArquivo { Nome = Path.GetFileName(d), Caminho = d, Pasta = true });
        }
        foreach (var f in Directory.EnumerateFiles(pasta))
        {
            if (EhOculto(f)) continue;
            long tam = 0;
            try { tam = new FileInfo(f).Length; } catch (IOException) { }
            itens.Add(new ItemArquivo { Nome = Path.GetFileName(f), Caminho = f, Pasta = false, Tamanho = tam });
        }
        return itens.OrderByDescending(i => i.Pasta).ThenBy(i => i.Nome, StringComparer.OrdinalIgnoreCase).ToList();
    }

    private static bool EhOculto(string caminho)
    {
        try
        {
            var atributos = File.GetAttributes(caminho);
            return atributos.HasFlag(FileAttributes.Hidden) || atributos.HasFlag(FileAttributes.System);
        }
        catch (Exception) { return true; }
    }

    public const int TamanhoBloco = 128 * 1024;

    /// <summary>Lê um bloco do arquivo para envio ao celular. Devolve null quando acabou.</summary>
    public byte[]? LerBloco(string caminho, long offset, out long tamanhoTotal)
    {
        tamanhoTotal = 0;
        if (!CaminhoPermitido(caminho, out var completo) || !File.Exists(completo))
            throw new UnauthorizedAccessException("Arquivo fora das áreas autorizadas.");

        using var fs = new FileStream(completo, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
        tamanhoTotal = fs.Length;
        if (offset >= fs.Length) return null;
        fs.Seek(offset, SeekOrigin.Begin);
        var restante = (int)Math.Min(TamanhoBloco, fs.Length - offset);
        var buffer = new byte[restante];
        var lidos = fs.Read(buffer, 0, restante);
        return lidos == restante ? buffer : buffer[..lidos];
    }

    /// <summary>Pasta onde os arquivos enviados pelo celular são gravados.</summary>
    public string PastaRecebidos()
    {
        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "PCFlow");
        Directory.CreateDirectory(pasta);
        return pasta;
    }

    /// <summary>
    /// Reduz um nome vindo da rede ao último componente, tratando '/' e '\' como
    /// separadores independentemente do sistema operacional. Path.GetFileName sozinho
    /// não serve: fora do Windows ele ignora '\' e deixaria passar "..\..\alvo.txt".
    /// </summary>
    public static string NomeSeguro(string nome)
    {
        var ultimo = nome.Split('/', '\\').LastOrDefault() ?? "";
        ultimo = ultimo.Trim().TrimStart('.');
        foreach (var invalido in Path.GetInvalidFileNameChars())
            ultimo = ultimo.Replace(invalido, '_');
        // ':' separa fluxos alternativos no NTFS e não é inválido em todo sistema.
        ultimo = ultimo.Replace(':', '_');
        return ultimo;
    }

    /// <summary>Grava um bloco recebido. offset 0 recria o arquivo (retomada usa offset &gt; 0).</summary>
    public string GravarBloco(string nome, long offset, byte[] dados)
    {
        var seguro = NomeSeguro(nome);
        if (string.IsNullOrWhiteSpace(seguro)) throw new ArgumentException("Nome de arquivo inválido.");
        var destino = Path.Combine(PastaRecebidos(), seguro);

        using var fs = new FileStream(destino, offset == 0 ? FileMode.Create : FileMode.OpenOrCreate,
            FileAccess.Write, FileShare.Read);
        fs.Seek(offset, SeekOrigin.Begin);
        fs.Write(dados, 0, dados.Length);
        return destino;
    }
}
