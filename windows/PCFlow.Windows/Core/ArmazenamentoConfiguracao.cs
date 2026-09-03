using System.Text.Json;

namespace PCFlow.Windows.Core;

public sealed class ArmazenamentoConfiguracao
{
    private readonly string _arquivo;
    private readonly JsonSerializerOptions _json = new() { WriteIndented = true };

    public ArmazenamentoConfiguracao()
    {
        var pasta = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PCFlow");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "configuracao.json");
    }

    public ConfiguracaoPcFlow Carregar()
    {
        try
        {
            if (!File.Exists(_arquivo)) return new();
            return JsonSerializer.Deserialize<ConfiguracaoPcFlow>(File.ReadAllText(_arquivo), _json) ?? new();
        }
        catch
        {
            return new();
        }
    }

    public void Salvar(ConfiguracaoPcFlow configuracao)
        => File.WriteAllText(_arquivo, JsonSerializer.Serialize(configuracao, _json));
}
