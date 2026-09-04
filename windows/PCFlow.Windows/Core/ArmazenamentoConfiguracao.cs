using System.IO;
using System.Security.Cryptography;
using System.Text.Json;

namespace PCFlow.Windows.Core;

public sealed class ArmazenamentoConfiguracao
{
    private readonly string _arquivo;
    private readonly JsonSerializerOptions _json = new() { WriteIndented = true };
    private readonly object _lock = new();

    public ArmazenamentoConfiguracao()
    {
        var pasta = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PCFlow");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "configuracao.json");
    }

    public ConfiguracaoPcFlow Carregar()
    {
        lock (_lock)
        {
            ConfiguracaoPcFlow configuracao;
            try
            {
                configuracao = File.Exists(_arquivo)
                    ? JsonSerializer.Deserialize<ConfiguracaoPcFlow>(File.ReadAllText(_arquivo), _json) ?? new()
                    : new();
            }
            catch { configuracao = new(); }

            if (string.IsNullOrWhiteSpace(configuracao.MaquinaId))
            {
                configuracao.MaquinaId = RandomNumberGenerator.GetInt32(100_000_000, 999_999_999).ToString();
                SalvarSemLock(configuracao);
            }
            return configuracao;
        }
    }

    public void Salvar(ConfiguracaoPcFlow configuracao)
    {
        lock (_lock) SalvarSemLock(configuracao);
    }

    private void SalvarSemLock(ConfiguracaoPcFlow configuracao)
        => File.WriteAllText(_arquivo, JsonSerializer.Serialize(configuracao, _json));
}
