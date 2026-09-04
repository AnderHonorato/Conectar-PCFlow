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

    /// <summary>Como este dispositivo chegou na última vez: "local", "internet" ou "servidor".</summary>
    public string UltimaOrigem { get; set; } = "local";

    /// <summary>
    /// Quando falso, o dispositivo herda as permissões gerais do PC.
    /// Quando verdadeiro, valem exclusivamente as permissões abaixo.
    /// </summary>
    public bool PermissoesProprias { get; set; }

    public bool PermitirTela { get; set; } = true;
    public bool PermitirEntrada { get; set; } = true;
    public bool PermitirClipboard { get; set; } = true;
    public bool PermitirEnergia { get; set; } = true;
    public bool PermitirArquivos { get; set; } = true;

    /// <summary>Este dispositivo pode entrar de fora da rede local.</summary>
    public bool PermitirForaDaRede { get; set; } = true;

    /// <summary>Permissões que valem para esta conexão, já resolvendo a herança.</summary>
    public PermissoesEfetivas Resolver(ConfiguracaoPcFlow geral) => PermissoesProprias
        ? new PermissoesEfetivas(PermitirTela, PermitirEntrada, PermitirClipboard, PermitirEnergia, PermitirArquivos)
        : new PermissoesEfetivas(geral.PermitirTela, geral.PermitirEntrada, geral.PermitirClipboard,
                                 geral.PermitirEnergia, geral.PermitirArquivos);
}

public sealed record PermissoesEfetivas(
    bool Tela, bool Entrada, bool Clipboard, bool Energia, bool Arquivos);

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

    // --- Acesso de fora da rede local ---

    /// <summary>Aceitar conexões que não vêm da rede local (internet ou servidor).</summary>
    public bool PermitirAcessoExterno { get; set; }

    /// <summary>Tentar abrir as portas no roteador por UPnP ao ligar o servidor.</summary>
    public bool AbrirPortasUpnp { get; set; } = true;

    /// <summary>
    /// Endereço do servidor de retransmissão (host:porta). Vazio = sem servidor,
    /// funcionando só por rede local e por IP público/UPnP.
    /// </summary>
    public string ServidorRelay { get; set; } = "";

    /// <summary>Usar o servidor de retransmissão quando ele estiver configurado.</summary>
    public bool UsarServidorRelay { get; set; }

    /// <summary>Segredo que prova a posse do código neste servidor de retransmissão.</summary>
    public string SegredoRelay { get; set; } = "";

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

/// <summary>
/// De onde a conexão veio. O servidor trata rede local e internet com exigências
/// diferentes, então essa classificação precisa acompanhar a sessão inteira.
/// </summary>
public sealed record OrigemConexao(string Descricao, bool RedeLocal, string Rotulo)
{
    public static OrigemConexao Local(string endereco) => new(endereco, true, "local");
    public static OrigemConexao Internet(string endereco) => new(endereco, false, "internet");
    public static OrigemConexao Servidor() => new("servidor de retransmissão", false, "servidor");
}
