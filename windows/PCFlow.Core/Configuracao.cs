using System.Text.Json;
using System.Text.Json.Serialization;

namespace PCFlow.Core;

public sealed class DispositivoAutorizado
{
    public string Id { get; set; } = "";
    public string Nome { get; set; } = "Celular";
    public string Modelo { get; set; } = "";
    public string Token { get; set; } = "";
    public bool Bloqueado { get; set; }
    public string UltimoIp { get; set; } = "";
    public DateTime PareadoEm { get; set; } = DateTime.Now;
    public DateTime UltimaConexao { get; set; } = DateTime.Now;

    [JsonIgnore] public bool Conectado { get; set; }
}

public enum AcaoAoFechar { MinimizarParaBandeja = 0, Encerrar = 1, PerguntarSempre = 2 }

public sealed class ConfiguracaoPcFlow
{
    public AcaoAoFechar AoFechar { get; set; } = AcaoAoFechar.MinimizarParaBandeja;
    public bool IniciarComWindows { get; set; }
    public bool IniciarServidorAutomaticamente { get; set; } = true;
    public bool AbrirMinimizado { get; set; }
    public bool SomenteRedeLocal { get; set; } = true;
    public bool PerguntarAntesDeNovoDispositivo { get; set; } = true;
    public bool ConfirmarDesligarReiniciar { get; set; } = true;
    public bool PermitirEnergia { get; set; } = true;
    public bool PermitirArquivos { get; set; } = true;
    public bool PermitirTelaRemota { get; set; } = true;
    public bool SincronizarAreaTransferencia { get; set; } = true;
    public int PortaControle { get; set; } = Protocolo.PortaControle;

    /// <summary>Pastas que o celular pode navegar. Vazio = apenas as pastas padrão do usuário.</summary>
    public List<string> PastasCompartilhadas { get; set; } = [];
    public List<DispositivoAutorizado> Dispositivos { get; set; } = [];

    /// <summary>Janela: preservada entre execuções, mas sempre validada contra a tela atual.</summary>
    public double JanelaLargura { get; set; }
    public double JanelaAltura { get; set; }
}

/// <summary>
/// Persistência da configuração em %AppData%\PCFlow\configuracao.json.
/// Todas as gravações são serializadas por um lock: várias sessões de celular
/// gravam ao mesmo tempo e a versão anterior corrompia/derrubava o app com IOException.
/// </summary>
public sealed class ArmazenamentoConfiguracao
{
    private readonly string _arquivo;
    private readonly object _trava = new();
    private static readonly JsonSerializerOptions Opcoes = new(JsonSerializerDefaults.Web) { WriteIndented = true };

    public string Caminho => _arquivo;

    public ArmazenamentoConfiguracao(string? pastaBase = null)
    {
        var pasta = pastaBase ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PCFlow");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "configuracao.json");
    }

    public ConfiguracaoPcFlow Carregar()
    {
        lock (_trava)
        {
            try
            {
                if (!File.Exists(_arquivo)) return new ConfiguracaoPcFlow();
                var texto = File.ReadAllText(_arquivo);
                return JsonSerializer.Deserialize<ConfiguracaoPcFlow>(texto, Opcoes) ?? new ConfiguracaoPcFlow();
            }
            catch (Exception)
            {
                // Configuração corrompida não pode impedir o app de abrir.
                return new ConfiguracaoPcFlow();
            }
        }
    }

    public void Salvar(ConfiguracaoPcFlow configuracao)
    {
        lock (_trava)
        {
            try
            {
                var temporario = _arquivo + ".tmp";
                File.WriteAllText(temporario, JsonSerializer.Serialize(configuracao, Opcoes));
                File.Move(temporario, _arquivo, overwrite: true);
            }
            catch (IOException) { /* disco ocupado: a próxima gravação resolve */ }
            catch (UnauthorizedAccessException) { }
        }
    }
}
