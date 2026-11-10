using System.Text.Json.Serialization;

namespace PCFlow.Core;

public sealed class DispositivoAutorizado
{
    public string Id { get; set; } = "";
    public string Nome { get; set; } = "Celular";
    public string Token { get; set; } = "";
    public bool Revogado { get; set; }
    public DateTime PareadoEm { get; set; } = DateTime.UtcNow;
    public DateTime UltimaConexao { get; set; } = DateTime.UtcNow;

    [JsonIgnore]
    public string Situacao => Revogado ? "Revogado" : "Autorizado";
}

public sealed class ConfiguracaoPcFlow
{
    public bool MinimizarParaBandeja { get; set; } = true;
    public bool IniciarServidorAutomaticamente { get; set; } = true;
    public string HostDeviceId { get; set; } = "";
    public List<DispositivoAutorizado> Dispositivos { get; set; } = [];
}

public sealed record RemoteCommand(
    string Name,
    double X = 0,
    double Y = 0,
    int Delta = 0,
    string? Button = null,
    string? Direction = null,
    string? Text = null,
    string? Key = null,
    string? Action = null);

public interface ICommandSink
{
    void Execute(RemoteCommand command);
    void ReleaseHeld(string kind, string code);
}
