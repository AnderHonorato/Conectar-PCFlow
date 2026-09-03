using System.Text.Json.Serialization;

namespace PCFlow.Windows.Core;

public sealed class MensagemRede
{
    [JsonPropertyName("tipo")] public string Tipo { get; set; } = "";
    [JsonPropertyName("acao")] public string? Acao { get; set; }
    [JsonPropertyName("dispositivoId")] public string? DispositivoId { get; set; }
    [JsonPropertyName("nome")] public string? Nome { get; set; }
    [JsonPropertyName("token")] public string? Token { get; set; }
    [JsonPropertyName("pin")] public string? Pin { get; set; }
    [JsonPropertyName("x")] public double X { get; set; }
    [JsonPropertyName("y")] public double Y { get; set; }
    [JsonPropertyName("delta")] public int Delta { get; set; }
    [JsonPropertyName("botao")] public string? Botao { get; set; }
    [JsonPropertyName("texto")] public string? Texto { get; set; }
    [JsonPropertyName("tecla")] public string? Tecla { get; set; }
    [JsonPropertyName("mensagem")] public string? Mensagem { get; set; }
}

public sealed class DispositivoAutorizado
{
    public string Id { get; set; } = "";
    public string Nome { get; set; } = "Celular";
    public string Token { get; set; } = "";
    public DateTime PareadoEm { get; set; } = DateTime.UtcNow;
    public DateTime UltimaConexao { get; set; } = DateTime.UtcNow;
}

public sealed class ConfiguracaoPcFlow
{
    public bool MinimizarParaBandeja { get; set; } = true;
    public bool IniciarServidorAutomaticamente { get; set; } = true;
    public List<DispositivoAutorizado> Dispositivos { get; set; } = [];
}
