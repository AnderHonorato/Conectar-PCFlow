using PCFlow.Windows.Core;
using PCFlow.Windows.Preferencias;

namespace PCFlow.Windows.Logica;

public enum StatusDispositivo { Confiavel, Recente, Bloqueado }

public enum OrdemDispositivos { MaisRecente, Nome, Favoritos }

/// <summary>
/// Um dispositivo como a tela precisa dele: o que o servidor guarda
/// (<see cref="DispositivoAutorizado"/>) somado ao que é só da interface
/// (apelido, descrição, favorito), já com texto pronto para exibir.
/// </summary>
public sealed class DispositivoVisao
{
    public string Id { get; init; } = "";
    public string NomeInformado { get; init; } = "";
    public string Apelido { get; init; } = "";
    public string Descricao { get; init; } = "";
    public string Sistema { get; init; } = "";
    public bool Favorito { get; init; }
    public bool Bloqueado { get; init; }
    public DateTime UltimaConexao { get; init; }
    public string UltimaOrigem { get; init; } = "local";
    public DateTime PareadoEm { get; init; }
    public bool PermissoesProprias { get; init; }
    public bool PermitirTela { get; init; }
    public bool PermitirEntrada { get; init; }
    public bool PermitirClipboard { get; init; }
    public bool PermitirEnergia { get; init; }
    public bool PermitirArquivos { get; init; }
    public bool PermitirForaDaRede { get; init; }

    /// <summary>Data usada para decidir se o dispositivo é "recente".</summary>
    public DateTime Referencia { get; init; } = DateTime.Now;

    public string Nome => string.IsNullOrWhiteSpace(Apelido) ? NomeInformado : Apelido;
    public string IdFormatado => FormatoIdentificador.EmGrupos(Id);
    public string SistemaTexto => string.IsNullOrWhiteSpace(Sistema) ? "Não informado" : Sistema;
    public string UltimaConexaoTexto => FormatoIdentificador.QuandoRelativo(UltimaConexao, Referencia);
    public string PareadoEmTexto => PareadoEm == default ? "—" : PareadoEm.ToString("dd/MM/yyyy");
    public string DescricaoOuVazio => string.IsNullOrWhiteSpace(Descricao) ? "Sem descrição" : Descricao;

    public StatusDispositivo Status =>
        Bloqueado ? StatusDispositivo.Bloqueado
        : (Referencia - UltimaConexao).TotalDays <= 7 ? StatusDispositivo.Recente
        : StatusDispositivo.Confiavel;

    public string StatusTexto => Status switch
    {
        StatusDispositivo.Bloqueado => "Bloqueado",
        StatusDispositivo.Recente => "Recente",
        _ => "Confiável"
    };

    public string OrigemTexto => UltimaOrigem switch
    {
        "internet" => "Pela internet",
        "servidor" => "Por servidor de retransmissão",
        _ => "Rede local"
    };

    /// <summary>
    /// Miniatura do dispositivo: duas cores derivadas do Id, sempre as mesmas
    /// para o mesmo aparelho. Nada de foto de banco de imagem.
    /// </summary>
    public string CorMiniatura => PadraoIdentidade.Cor(Id, 0);
    public string CorMiniaturaSecundaria => PadraoIdentidade.Cor(Id, 1);
    public string Iniciais => PadraoIdentidade.Iniciais(Nome);

    public static DispositivoVisao De(DispositivoAutorizado dispositivo, InfoDispositivoInterface? extra, DateTime? agora = null)
        => new()
        {
            Id = dispositivo.Id,
            NomeInformado = dispositivo.Nome,
            Apelido = extra?.Apelido ?? "",
            Descricao = extra?.Descricao ?? "",
            Sistema = extra?.Sistema ?? "",
            Favorito = extra?.Favorito ?? false,
            Bloqueado = dispositivo.Bloqueado,
            UltimaConexao = dispositivo.UltimaConexao.ToLocalTime(),
            UltimaOrigem = dispositivo.UltimaOrigem,
            PareadoEm = dispositivo.PareadoEm.ToLocalTime(),
            PermissoesProprias = dispositivo.PermissoesProprias,
            PermitirTela = dispositivo.PermitirTela,
            PermitirEntrada = dispositivo.PermitirEntrada,
            PermitirClipboard = dispositivo.PermitirClipboard,
            PermitirEnergia = dispositivo.PermitirEnergia,
            PermitirArquivos = dispositivo.PermitirArquivos,
            PermitirForaDaRede = dispositivo.PermitirForaDaRede,
            Referencia = agora ?? DateTime.Now
        };
}

/// <summary>Busca, filtro, ordenação, contagem por aba e paginação da lista.</summary>
public static class ListaDispositivos
{
    public const int PorPagina = 6;

    /// <summary>
    /// Casa o texto contra nome, apelido, descrição, Id (com e sem grupos) e
    /// sistema. Sem diferenciar maiúscula nem acento — quem procura "joao"
    /// espera achar "João".
    /// </summary>
    public static bool Casa(DispositivoVisao dispositivo, string? busca)
    {
        if (string.IsNullOrWhiteSpace(busca)) return true;
        var alvo = Normalizar(busca);
        return Normalizar(dispositivo.Nome).Contains(alvo)
            || Normalizar(dispositivo.NomeInformado).Contains(alvo)
            || Normalizar(dispositivo.Descricao).Contains(alvo)
            || Normalizar(dispositivo.Sistema).Contains(alvo)
            || dispositivo.Id.Contains(FormatoIdentificador.SemGrupos(busca), StringComparison.OrdinalIgnoreCase)
            || Normalizar(dispositivo.IdFormatado).Contains(alvo);
    }

    public static IReadOnlyList<DispositivoVisao> Filtrar(
        IEnumerable<DispositivoVisao> todos,
        string? busca = null,
        StatusDispositivo? aba = null,
        string? sistema = null,
        OrdemDispositivos ordem = OrdemDispositivos.MaisRecente)
    {
        var filtrados = todos.Where(d => Casa(d, busca));
        if (aba is not null) filtrados = filtrados.Where(d => d.Status == aba);
        if (!string.IsNullOrWhiteSpace(sistema))
            filtrados = filtrados.Where(d => string.Equals(d.SistemaTexto, sistema, StringComparison.OrdinalIgnoreCase));

        return ordem switch
        {
            OrdemDispositivos.Nome => filtrados
                .OrderBy(d => d.Nome, StringComparer.CurrentCultureIgnoreCase).ToList(),
            OrdemDispositivos.Favoritos => filtrados
                .OrderByDescending(d => d.Favorito)
                .ThenByDescending(d => d.UltimaConexao).ToList(),
            _ => filtrados
                .OrderByDescending(d => d.UltimaConexao)
                .ThenBy(d => d.Nome, StringComparer.CurrentCultureIgnoreCase).ToList()
        };
    }

    /// <summary>Contagem de cada aba. As abas são exclusivas: somadas dão o total.</summary>
    public static ContagemAbas Contar(IEnumerable<DispositivoVisao> todos)
    {
        var lista = todos as IList<DispositivoVisao> ?? todos.ToList();
        return new ContagemAbas(
            lista.Count,
            lista.Count(d => d.Status == StatusDispositivo.Confiavel),
            lista.Count(d => d.Status == StatusDispositivo.Recente),
            lista.Count(d => d.Status == StatusDispositivo.Bloqueado));
    }

    /// <summary>Sistemas presentes na lista, para alimentar o filtro.</summary>
    public static IReadOnlyList<string> Sistemas(IEnumerable<DispositivoVisao> todos)
        => todos.Select(d => d.SistemaTexto)
                .Distinct(StringComparer.CurrentCultureIgnoreCase)
                .OrderBy(s => s, StringComparer.CurrentCultureIgnoreCase)
                .ToList();

    public static int TotalPaginas(int itens, int porPagina = PorPagina)
        => itens <= 0 ? 1 : (int)Math.Ceiling(itens / (double)Math.Max(1, porPagina));

    public static IReadOnlyList<DispositivoVisao> Pagina(
        IReadOnlyList<DispositivoVisao> lista, int pagina, int porPagina = PorPagina)
    {
        var total = TotalPaginas(lista.Count, porPagina);
        var atual = Math.Clamp(pagina, 1, total);
        return lista.Skip((atual - 1) * porPagina).Take(porPagina).ToList();
    }

    private static string Normalizar(string? texto)
    {
        if (string.IsNullOrEmpty(texto)) return "";
        var decomposto = texto.Normalize(System.Text.NormalizationForm.FormD);
        var limpo = new System.Text.StringBuilder(decomposto.Length);
        foreach (var c in decomposto)
            if (System.Globalization.CharUnicodeInfo.GetUnicodeCategory(c)
                != System.Globalization.UnicodeCategory.NonSpacingMark)
                limpo.Append(char.ToLowerInvariant(c));
        return limpo.ToString();
    }
}

public sealed record ContagemAbas(int Todos, int Confiaveis, int Recentes, int Bloqueados);

/// <summary>
/// Identidade visual determinística a partir do Id: sempre a mesma cor para o
/// mesmo dispositivo, em qualquer máquina, sem guardar nada.
/// </summary>
public static class PadraoIdentidade
{
    private static readonly string[] Paleta =
    [
        "#F2AA2E", "#14D3C3", "#5B9DFF", "#B98BFF", "#57C97A", "#FF9E64", "#4FC3E8", "#E8709B"
    ];

    public static string Cor(string? id, int deslocamento)
    {
        if (string.IsNullOrEmpty(id)) return Paleta[0];
        var soma = id.Sum(c => c) + deslocamento * 3;
        return Paleta[Math.Abs(soma) % Paleta.Length];
    }

    public static string Iniciais(string? nome)
    {
        if (string.IsNullOrWhiteSpace(nome)) return "?";
        var partes = nome.Split([' ', '-', '_'], StringSplitOptions.RemoveEmptyEntries);
        return partes.Length == 1
            ? partes[0][..Math.Min(2, partes[0].Length)].ToUpperInvariant()
            : $"{partes[0][0]}{partes[1][0]}".ToUpperInvariant();
    }
}
