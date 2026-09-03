using System.Text.Json;
using System.Text.Json.Serialization;

namespace PCFlow.Core;

/// <summary>
/// Constantes e utilidades do protocolo PCFlow (JSON por linha sobre TCP).
/// A versão é negociada no handshake para permitir evolução sem quebrar clientes antigos.
/// </summary>
public static class Protocolo
{
    public const int Versao = 2;
    public const string VersaoApp = "1.0.0";

    public const int PortaDescoberta = 45455;
    public const int PortaControle = 45456;

    /// <summary>Sonda enviada pelo Android em broadcast UDP.</summary>
    public const string Sonda = "PCFLOW_DISCOVER_V2";

    /// <summary>Limite de bytes por linha. Protege contra mensagens gigantes (DoS).</summary>
    public const int TamanhoMaximoLinha = 256 * 1024;

    /// <summary>Intervalo em que o servidor espera um sinal de vida do cliente.</summary>
    public static readonly TimeSpan TempoLimiteInatividade = TimeSpan.FromSeconds(25);

    /// <summary>Validade do PIN de pareamento.</summary>
    public static readonly TimeSpan ValidadePin = TimeSpan.FromMinutes(3);

    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        NumberHandling = JsonNumberHandling.AllowReadingFromString
    };

    public static string Serializar<T>(T valor) => JsonSerializer.Serialize(valor, Json);

    public static Mensagem? Desserializar(string linha)
    {
        try { return JsonSerializer.Deserialize<Mensagem>(linha, Json); }
        catch (JsonException) { return null; }
    }
}

/// <summary>
/// Mensagem única do protocolo. Campos opcionais mantêm o formato simples e
/// compatível entre Windows e Android sem gerar uma classe por comando.
/// </summary>
public sealed class Mensagem
{
    [JsonPropertyName("tipo")] public string Tipo { get; set; } = "";
    [JsonPropertyName("acao")] public string? Acao { get; set; }
    [JsonPropertyName("protocolo")] public int ProtocoloVersao { get; set; }

    // Handshake
    [JsonPropertyName("dispositivoId")] public string? DispositivoId { get; set; }
    [JsonPropertyName("nome")] public string? Nome { get; set; }
    [JsonPropertyName("modelo")] public string? Modelo { get; set; }
    [JsonPropertyName("token")] public string? Token { get; set; }
    [JsonPropertyName("pin")] public string? Pin { get; set; }
    [JsonPropertyName("codigo")] public string? Codigo { get; set; }
    [JsonPropertyName("mensagem")] public string? Texto0 { get; set; }
    [JsonPropertyName("versao")] public string? Versao { get; set; }

    // Ponteiro
    [JsonPropertyName("dx")] public double Dx { get; set; }
    [JsonPropertyName("dy")] public double Dy { get; set; }
    [JsonPropertyName("x")] public double X { get; set; }
    [JsonPropertyName("y")] public double Y { get; set; }
    [JsonPropertyName("botao")] public string? Botao { get; set; }

    // Teclado
    [JsonPropertyName("texto")] public string? Texto { get; set; }
    [JsonPropertyName("tecla")] public string? Tecla { get; set; }
    [JsonPropertyName("mods")] public List<string>? Modificadores { get; set; }

    // Diagnóstico / arquivos / tela
    [JsonPropertyName("t")] public long Carimbo { get; set; }
    [JsonPropertyName("caminho")] public string? Caminho { get; set; }
    [JsonPropertyName("destino")] public string? Destino { get; set; }
    [JsonPropertyName("offset")] public long Offset { get; set; }
    [JsonPropertyName("tamanho")] public long Tamanho { get; set; }
    [JsonPropertyName("dados")] public string? Dados { get; set; }
    [JsonPropertyName("fim")] public bool Fim { get; set; }
    [JsonPropertyName("qualidade")] public int Qualidade { get; set; }
    [JsonPropertyName("fps")] public int Fps { get; set; }
    [JsonPropertyName("largura")] public int Largura { get; set; }
    [JsonPropertyName("altura")] public int Altura { get; set; }
    [JsonPropertyName("itens")] public List<ItemArquivo>? Itens { get; set; }
    [JsonPropertyName("ok")] public bool Ok { get; set; }
}

public sealed class ItemArquivo
{
    [JsonPropertyName("nome")] public string Nome { get; set; } = "";
    [JsonPropertyName("caminho")] public string Caminho { get; set; } = "";
    [JsonPropertyName("pasta")] public bool Pasta { get; set; }
    [JsonPropertyName("tamanho")] public long Tamanho { get; set; }
}
