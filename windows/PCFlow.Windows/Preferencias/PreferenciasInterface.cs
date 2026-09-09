using System.Text.Json;
using System.Text.Json.Serialization;

namespace PCFlow.Windows.Preferencias;

/// <summary>
/// Tudo que é da interface e não do motor: tema, cor de destaque, apelido e
/// favorito de cada dispositivo, e o registro de atividade de segurança.
///
/// Fica em arquivo próprio de propósito. A configuração do servidor
/// (<c>ConfiguracaoPcFlow</c>) é território de outra frente e não pode crescer
/// com campo de aparência; o casamento entre os dois é feito pelo Id do
/// dispositivo.
/// </summary>
public sealed class PreferenciasInterface
{
    /// <summary>"escuro", "claro" ou "automatico".</summary>
    public string Tema { get; set; } = "escuro";

    /// <summary>Cor de destaque em #RRGGBB. Precisa ser uma das ofertadas na tela.</summary>
    public string CorDestaque { get; set; } = "#F2AA2E";

    public bool IniciarMinimizado { get; set; }
    public bool AvisarConexao { get; set; } = true;
    public bool AvisarTransferencia { get; set; } = true;
    public bool AvisarNovoDispositivo { get; set; } = true;

    /// <summary>Última página aberta, para o app reabrir onde parou.</summary>
    public string PaginaInicial { get; set; } = "inicio";

    // --- prévia da tela na página Acesso Remoto ---
    public int PreviaMonitor { get; set; }
    public int PreviaQualidade { get; set; } = 62;
    public int PreviaFps { get; set; } = 15;
    public bool PreviaLigada { get; set; } = true;

    /// <summary>Pasta que a aba Transferência abre no painel local.</summary>
    public string PastaTransferencia { get; set; } = "";

    public List<InfoDispositivoInterface> Dispositivos { get; set; } = [];

    /// <summary>
    /// Registro de atividade de segurança. Guarda **apenas metadados**:
    /// que tipo de evento, com que dispositivo, de que endereço e quando.
    /// Nunca tecla digitada, conteúdo de tela, texto de área de transferência
    /// ou senha — nem em forma cifrada.
    /// </summary>
    public List<EventoSeguranca> Atividade { get; set; } = [];

    public InfoDispositivoInterface Para(string id)
    {
        var achado = Dispositivos.FirstOrDefault(d => d.Id == id);
        if (achado is not null) return achado;
        achado = new InfoDispositivoInterface { Id = id };
        Dispositivos.Add(achado);
        return achado;
    }

    /// <summary>Anota um evento e mantém a lista curta — isto é diário, não banco.</summary>
    public void Anotar(string tipo, string descricao, string origem)
    {
        Atividade.Insert(0, new EventoSeguranca
        {
            Tipo = tipo,
            Descricao = descricao,
            Origem = origem,
            Quando = DateTime.Now
        });
        if (Atividade.Count > LimiteAtividade) Atividade.RemoveRange(LimiteAtividade, Atividade.Count - LimiteAtividade);
    }

    public const int LimiteAtividade = 200;

    // ------------------------------------------------------------------
    // Serialização (testada sem tocar em disco)
    // ------------------------------------------------------------------

    private static readonly JsonSerializerOptions Formato = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.Never
    };

    public string ParaJson() => JsonSerializer.Serialize(this, Formato);

    /// <summary>Arquivo corrompido nunca derruba o app: vale o padrão.</summary>
    public static PreferenciasInterface DeJson(string? json)
    {
        if (string.IsNullOrWhiteSpace(json)) return new PreferenciasInterface();
        try { return JsonSerializer.Deserialize<PreferenciasInterface>(json, Formato) ?? new PreferenciasInterface(); }
        catch (JsonException) { return new PreferenciasInterface(); }
    }
}

public sealed class InfoDispositivoInterface
{
    public string Id { get; set; } = "";

    /// <summary>Nome dado pelo dono. Vazio significa usar o nome que o aparelho informou.</summary>
    public string Apelido { get; set; } = "";

    public string Descricao { get; set; } = "";
    public bool Favorito { get; set; }

    /// <summary>Sistema informado pelo aparelho, quando ele informa. Vazio = desconhecido.</summary>
    public string Sistema { get; set; } = "";
}

public sealed class EventoSeguranca
{
    /// <summary>"aceita", "negada", "bloqueio", "liberacao", "revogacao", "senha".</summary>
    public string Tipo { get; set; } = "";

    public string Descricao { get; set; } = "";
    public string Origem { get; set; } = "";
    public DateTime Quando { get; set; } = DateTime.Now;
}
