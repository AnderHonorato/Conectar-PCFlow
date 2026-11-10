using System.Text.Json;

namespace PCFlow.Core;

public sealed class ArmazenamentoConfiguracao
{
    private readonly string _arquivo;
    private readonly JsonSerializerOptions _json = new() { WriteIndented = true };

    public ArmazenamentoConfiguracao(string? pasta = null)
    {
        pasta ??= Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PCFlow");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "configuracao.json");
    }

    public ConfiguracaoPcFlow Carregar()
    {
        try
        {
            if (!File.Exists(_arquivo))
                return Novo();
            var loaded = JsonSerializer.Deserialize<ConfiguracaoPcFlow>(File.ReadAllText(_arquivo), _json) ?? Novo();
            if (string.IsNullOrWhiteSpace(loaded.HostDeviceId))
            {
                loaded.HostDeviceId = Guid.NewGuid().ToString("D");
                Salvar(loaded);
            }
            return loaded;
        }
        catch
        {
            return Novo();
        }
    }

    public void Salvar(ConfiguracaoPcFlow configuracao) =>
        File.WriteAllText(_arquivo, JsonSerializer.Serialize(configuracao, _json));

    private static ConfiguracaoPcFlow Novo() => new() { HostDeviceId = Guid.NewGuid().ToString("D") };
}
