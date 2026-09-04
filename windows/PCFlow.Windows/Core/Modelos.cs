using System.Text.Json.Serialization;

namespace PCFlow.Windows.Core;

public sealed class MensagemRede
{
    [JsonPropertyName("tipo")] public string Tipo { get; set; } = "";
    [JsonPropertyName("acao")] public string? Acao { get; set; }
    [JsonPropertyName("dispositivoId")] public string? DispositivoId { get; set; }
    [JsonPropertyName("maquinaId")] public string? MaquinaId { get; set; }
    [JsonPropertyName("nome")] public string? Nome { get; set; }
    [JsonPropertyName("token")] public string? Token { get; set; }
    [JsonPropertyName("sessaoId")] public string? SessaoId { get; set; }
    [JsonPropertyName("pin")] public string? Pin { get; set; }
    [JsonPropertyName("senha")] public string? Senha { get; set; }
    [JsonPropertyName("x")] public double X { get; set; }
    [JsonPropertyName("y")] public double Y { get; set; }
    [JsonPropertyName("delta")] public int Delta { get; set; }
    [JsonPropertyName("botao")] public string? Botao { get; set; }
    [JsonPropertyName("texto")] public string? Texto { get; set; }
    [JsonPropertyName("tecla")] public string? Tecla { get; set; }
    [JsonPropertyName("monitor")] public int Monitor { get; set; }
    [JsonPropertyName("qualidade")] public int Qualidade { get; set; } = 68;
    [JsonPropertyName("fps")] public int Fps { get; set; } = 12;
    [JsonPropertyName("mensagem")] public string? Mensagem { get; set; }
    [JsonPropertyName("appVersao")] public string? AppVersao { get; set; }
}

public sealed class DispositivoAutorizado
{
    public string Id { get; set; } = "";
    public string Nome { get; set; } = "Celular";
    public string Token { get; set; } = "";
    public bool Bloqueado { get; set; }
    public DateTime PareadoEm { get; set; } = DateTime.UtcNow;
    public DateTime UltimaConexao { get; set; } = DateTime.UtcNow;
}

public sealed class ConfiguracaoPcFlow
{
    public string MaquinaId { get; set; } = "";
    public bool MinimizarParaBandeja { get; set; } = true;
    public bool IniciarServidorAutomaticamente { get; set; } = true;
    public bool DescobertaRede { get; set; } = true;
    public bool MolduraSessao { get; set; } = true;
    public string AcessoInterativo { get; set; } = "sempre";
    public string? SenhaSalt { get; set; }
    public string? SenhaHash { get; set; }
    public bool PermitirTela { get; set; } = true;
    public bool PermitirEntrada { get; set; } = true;
    public bool PermitirClipboard { get; set; } = true;
    public bool PermitirEnergia { get; set; } = true;
    public bool PermitirArquivos { get; set; } = true;
    public List<DispositivoAutorizado> Dispositivos { get; set; } = [];

    /// <summary>Tamanho da janela, sempre revalidado contra a tela atual ao abrir.</summary>
    public double JanelaLargura { get; set; }
    public double JanelaAltura { get; set; }
}

public sealed record SolicitacaoConexao(string DispositivoId, string Nome, string EnderecoIp, bool DispositivoConhecido);

public sealed record SessaoAtiva(
    string Id,
    string DispositivoId,
    string Nome,
    string EnderecoIp,
    bool PermitirTela,
    bool PermitirEntrada,
    bool PermitirClipboard,
    bool PermitirEnergia,
    bool PermitirArquivos);
