using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Xml.Linq;

namespace PCFlow.Windows.Core;

/// <summary>
/// Abre o caminho para o PC ser alcançado de fora da rede local.
///
/// Faz três coisas, nesta ordem:
///  1. acha o roteador por SSDP e pede a ele para encaminhar as portas (UPnP);
///  2. descobre o IP público real por HTTP;
///  3. compara os dois para detectar CGNAT — quando a operadora coloca o
///     cliente atrás de outro NAT, abrir porta no roteador não adianta e o
///     usuário precisa saber disso em vez de ficar tentando.
/// </summary>
public sealed class AcessoRemoto : IDisposable
{
    private static readonly IPEndPoint Ssdp = new(IPAddress.Parse("239.255.255.250"), 1900);

    private static readonly string[] ServicosWan =
    [
        "urn:schemas-upnp-org:service:WANIPConnection:2",
        "urn:schemas-upnp-org:service:WANIPConnection:1",
        "urn:schemas-upnp-org:service:WANPPPConnection:1"
    ];

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(6) };
    private readonly List<int> _portasMapeadas = [];
    private string? _urlControle;
    private string? _servico;
    private string? _ipLocal;

    public IReadOnlyList<int> PortasMapeadas => _portasMapeadas;

    /// <summary>
    /// Tenta deixar o PC acessível de fora. Nunca lança: o resultado explica em
    /// português o que aconteceu, inclusive quando não deu.
    /// </summary>
    public async Task<ResultadoAcessoExterno> AbrirAsync(IEnumerable<int> portas, CancellationToken ct = default)
    {
        var lista = portas.Distinct().ToArray();
        var ipPublico = await ObterIpPublicoAsync(ct);

        string? ipRoteador = null;
        var abertas = new List<int>();
        string? falhaUpnp = null;

        try
        {
            if (await LocalizarRoteadorAsync(ct))
            {
                ipRoteador = await ObterIpExternoDoRoteadorAsync(ct);
                foreach (var porta in lista)
                {
                    if (await MapearAsync(porta, "TCP", ct)) abertas.Add(porta);
                }
                await MapearAsync(ServidorPcFlow.PortaDescoberta, "UDP", ct);
            }
            else falhaUpnp = "Nenhum roteador com UPnP respondeu na rede.";
        }
        catch (Exception ex)
        {
            falhaUpnp = ex.Message;
        }

        _portasMapeadas.Clear();
        _portasMapeadas.AddRange(abertas);

        var cgnat = EhCgnat(ipRoteador, ipPublico);
        var sucesso = abertas.Count == lista.Length && !cgnat && ipPublico is not null;

        return new ResultadoAcessoExterno(
            Sucesso: sucesso,
            IpPublico: ipPublico,
            IpExternoDoRoteador: ipRoteador,
            PortasAbertas: abertas,
            Cgnat: cgnat,
            Detalhe: Explicar(sucesso, cgnat, ipPublico, ipRoteador, abertas, lista, falhaUpnp));
    }

    /// <summary>Desfaz o encaminhamento feito por <see cref="AbrirAsync"/>.</summary>
    public async Task FecharAsync(CancellationToken ct = default)
    {
        if (_urlControle is null) return;
        foreach (var porta in _portasMapeadas.ToArray())
            await RemoverAsync(porta, "TCP", ct);
        await RemoverAsync(ServidorPcFlow.PortaDescoberta, "UDP", ct);
        _portasMapeadas.Clear();
    }

    private static string Explicar(bool sucesso, bool cgnat, string? ipPublico, string? ipRoteador,
        List<int> abertas, int[] pedidas, string? falhaUpnp)
    {
        if (cgnat)
            return $"Sua operadora usa CGNAT: o roteador enxerga {ipRoteador}, que não é um endereço público " +
                   $"(o IP público visto na internet é {ipPublico ?? "desconhecido"}). Abrir porta no roteador não " +
                   "resolve nesse caso — use um servidor de retransmissão do PCFlow ou peça IP fixo à operadora.";
        if (sucesso)
            return $"Portas {string.Join(", ", abertas)} encaminhadas no roteador. O PC responde em {ipPublico}.";
        if (falhaUpnp is not null)
            return $"Não consegui configurar o roteador automaticamente ({falhaUpnp}). " +
                   $"Abra manualmente as portas {string.Join(", ", pedidas)} apontando para {ObterIpLocalPreferido()}, " +
                   "ou ligue o servidor de retransmissão.";
        if (ipPublico is null)
            return "Não consegui descobrir o IP público. Confira se este PC está com internet.";
        return $"Encaminhei {abertas.Count} de {pedidas.Length} portas. As que faltaram precisam ser abertas " +
               "manualmente no roteador ou pelo servidor de retransmissão.";
    }

    /// <summary>
    /// CGNAT: o roteador diz ter um endereço externo privado, ou ele não bate
    /// com o IP que a internet enxerga.
    /// </summary>
    private static bool EhCgnat(string? ipRoteador, string? ipPublico)
    {
        if (ipRoteador is null || !IPAddress.TryParse(ipRoteador, out var roteador)) return false;
        if (EhPrivadoOuCompartilhado(roteador)) return true;
        return ipPublico is not null && ipRoteador != ipPublico;
    }

    private static bool EhPrivadoOuCompartilhado(IPAddress endereco)
    {
        if (endereco.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = endereco.GetAddressBytes();
        return b[0] == 10
            || (b[0] == 192 && b[1] == 168)
            || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
            || (b[0] == 100 && b[1] >= 64 && b[1] <= 127) // faixa reservada ao CGNAT
            || (b[0] == 169 && b[1] == 254)
            || b[0] == 127;
    }

    public async Task<string?> ObterIpPublicoAsync(CancellationToken ct = default)
    {
        string[] servicos = ["https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com"];
        foreach (var servico in servicos)
        {
            try
            {
                var texto = (await _http.GetStringAsync(servico, ct)).Trim();
                if (IPAddress.TryParse(texto, out var ip) && ip.AddressFamily == AddressFamily.InterNetwork)
                    return ip.ToString();
            }
            catch (Exception) { /* tenta o próximo */ }
        }
        return null;
    }

    // ---------- UPnP ----------

    private async Task<bool> LocalizarRoteadorAsync(CancellationToken ct)
    {
        if (_urlControle is not null) return true;

        foreach (var alvo in new[] { "urn:schemas-upnp-org:device:InternetGatewayDevice:1", "ssdp:all" })
        {
            var local = await ProcurarDescricaoAsync(alvo, ct);
            if (local is null) continue;
            if (await LerDescricaoAsync(local, ct)) return true;
        }
        return false;
    }

    private static async Task<string?> ProcurarDescricaoAsync(string alvo, CancellationToken ct)
    {
        var pedido = Encoding.ASCII.GetBytes(
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            $"ST: {alvo}\r\n\r\n");

        using var udp = new UdpClient(new IPEndPoint(IPAddress.Any, 0)) { EnableBroadcast = true };
        await udp.SendAsync(pedido, Ssdp, ct);

        using var prazo = CancellationTokenSource.CreateLinkedTokenSource(ct);
        prazo.CancelAfter(TimeSpan.FromSeconds(3));
        try
        {
            while (!prazo.IsCancellationRequested)
            {
                var resposta = await udp.ReceiveAsync(prazo.Token);
                var texto = Encoding.ASCII.GetString(resposta.Buffer);
                if (!texto.Contains("InternetGatewayDevice", StringComparison.OrdinalIgnoreCase) &&
                    !texto.Contains("WANIPConnection", StringComparison.OrdinalIgnoreCase) &&
                    !texto.Contains("WANPPPConnection", StringComparison.OrdinalIgnoreCase)) continue;
                var local = Regex.Match(texto, @"(?im)^LOCATION:\s*(\S+)\s*$");
                if (local.Success) return local.Groups[1].Value;
            }
        }
        catch (OperationCanceledException) { }
        return null;
    }

    private async Task<bool> LerDescricaoAsync(string urlDescricao, CancellationToken ct)
    {
        var xml = XDocument.Parse(await _http.GetStringAsync(urlDescricao, ct));
        var ns = xml.Root?.GetDefaultNamespace() ?? XNamespace.None;

        foreach (var servico in ServicosWan)
        {
            var no = xml.Descendants(ns + "service")
                .FirstOrDefault(s => (string?)s.Element(ns + "serviceType") == servico);
            var controle = (string?)no?.Element(ns + "controlURL");
            if (string.IsNullOrWhiteSpace(controle)) continue;

            var raiz = new Uri(urlDescricao);
            _urlControle = new Uri(raiz, controle).ToString();
            _servico = servico;
            _ipLocal = ObterIpLocalNaRotaDe(raiz.Host);
            return true;
        }
        return false;
    }

    private async Task<bool> MapearAsync(int porta, string protocolo, CancellationToken ct)
    {
        var corpo =
            $"<NewRemoteHost></NewRemoteHost>" +
            $"<NewExternalPort>{porta}</NewExternalPort>" +
            $"<NewProtocol>{protocolo}</NewProtocol>" +
            $"<NewInternalPort>{porta}</NewInternalPort>" +
            $"<NewInternalClient>{_ipLocal}</NewInternalClient>" +
            $"<NewEnabled>1</NewEnabled>" +
            $"<NewPortMappingDescription>PCFlow {porta}/{protocolo}</NewPortMappingDescription>" +
            $"<NewLeaseDuration>0</NewLeaseDuration>";
        var resposta = await ChamarSoapAsync("AddPortMapping", corpo, ct);
        return resposta is not null;
    }

    private async Task RemoverAsync(int porta, string protocolo, CancellationToken ct)
    {
        var corpo =
            $"<NewRemoteHost></NewRemoteHost>" +
            $"<NewExternalPort>{porta}</NewExternalPort>" +
            $"<NewProtocol>{protocolo}</NewProtocol>";
        await ChamarSoapAsync("DeletePortMapping", corpo, ct);
    }

    private async Task<string?> ObterIpExternoDoRoteadorAsync(CancellationToken ct)
    {
        var resposta = await ChamarSoapAsync("GetExternalIPAddress", "", ct);
        if (resposta is null) return null;
        var achado = Regex.Match(resposta, @"<NewExternalIPAddress>([^<]*)</NewExternalIPAddress>");
        return achado.Success && achado.Groups[1].Value.Length > 0 ? achado.Groups[1].Value : null;
    }

    private async Task<string?> ChamarSoapAsync(string acao, string corpo, CancellationToken ct)
    {
        if (_urlControle is null || _servico is null) return null;
        var envelope =
            "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            $"<s:Body><u:{acao} xmlns:u=\"{_servico}\">{corpo}</u:{acao}></s:Body></s:Envelope>";

        using var pedido = new HttpRequestMessage(HttpMethod.Post, _urlControle)
        {
            Content = new StringContent(envelope, Encoding.UTF8, "text/xml")
        };
        pedido.Headers.TryAddWithoutValidation("SOAPAction", $"\"{_servico}#{acao}\"");

        try
        {
            var resposta = await _http.SendAsync(pedido, ct);
            if (!resposta.IsSuccessStatusCode) return null;
            return await resposta.Content.ReadAsStringAsync(ct);
        }
        catch (Exception) { return null; }
    }

    /// <summary>IP desta máquina na mesma interface que fala com o roteador.</summary>
    private static string ObterIpLocalNaRotaDe(string hostRoteador)
    {
        try
        {
            using var sonda = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            sonda.Connect(hostRoteador, 1900);
            return ((IPEndPoint)sonda.LocalEndPoint!).Address.ToString();
        }
        catch (SocketException) { return ObterIpLocalPreferido(); }
    }

    public static string ObterIpLocalPreferido()
    {
        try
        {
            using var sonda = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            sonda.Connect("8.8.8.8", 53);
            return ((IPEndPoint)sonda.LocalEndPoint!).Address.ToString();
        }
        catch (SocketException) { return "127.0.0.1"; }
    }

    public void Dispose() => _http.Dispose();
}

public sealed record ResultadoAcessoExterno(
    bool Sucesso,
    string? IpPublico,
    string? IpExternoDoRoteador,
    IReadOnlyList<int> PortasAbertas,
    bool Cgnat,
    string Detalhe);
