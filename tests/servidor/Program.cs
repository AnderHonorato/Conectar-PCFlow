using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using PCFlow.Windows.Core;

// Servidor de teste do PCFlow.
//
// Reproduz o caminho exato que estava quebrado: aceita TCP, faz o handshake TLS
// com TlsPcFlow (o mesmo arquivo usado pelo aplicativo Windows, incluído por
// link, sem cópia) e responde ao "ola" do Android como o servidor real responde.
//
// Serve para provar a correção sem depender de uma máquina Windows: o cliente
// que conecta aqui é o app Android de verdade rodando na JVM.

var porta = args.Length > 0 && int.TryParse(args[0], out var p) ? p : 45456;

using var certificado = CriarCertificado();
var impressao = Convert.ToHexString(SHA256.HashData(certificado.RawData)).ToLowerInvariant();

var ouvinte = new TcpListener(IPAddress.Loopback, porta);
ouvinte.Start();

Console.WriteLine($"PRONTO porta={porta} tls={impressao}");
Console.Out.Flush();

var json = new JsonSerializerOptions(JsonSerializerDefaults.Web);
using var encerrar = new CancellationTokenSource(TimeSpan.FromMinutes(2));

while (!encerrar.IsCancellationRequested)
{
    TcpClient cliente;
    try { cliente = await ouvinte.AcceptTcpClientAsync(encerrar.Token); }
    catch (OperationCanceledException) { break; }

    _ = Task.Run(async () =>
    {
        using (cliente)
        {
            var remoto = (cliente.Client.RemoteEndPoint as IPEndPoint)?.Address;
            cliente.NoDelay = true;
            using var ssl = new SslStream(cliente.GetStream(), false);
            try
            {
                var negociado = await TlsPcFlow.AutenticarServidorAsync(
                    ssl, certificado, encerrar.Token);
                Console.WriteLine($"TLS_OK {remoto} {negociado}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"TLS_FALHA {remoto} {TlsPcFlow.ExplicarFalha(ex)}");
                return;
            }

            var linha = await LerLinhaAsync(ssl, encerrar.Token);
            if (linha is null) { Console.WriteLine("SEM_HANDSHAKE"); return; }
            Console.WriteLine($"RECEBIDO {linha}");

            using var doc = JsonDocument.Parse(linha);
            var raiz = doc.RootElement;
            var tipo = raiz.TryGetProperty("tipo", out var t) ? t.GetString() : null;
            if (tipo != "ola") { Console.WriteLine("HANDSHAKE_INVALIDO"); return; }

            var versaoApp = raiz.TryGetProperty("appVersao", out var v) ? v.GetString() : null;
            if (!string.IsNullOrWhiteSpace(versaoApp) && versaoApp != VersaoPcFlow.App)
            {
                await EscreverAsync(ssl, new
                {
                    tipo = "erro",
                    mensagem = $"Versões diferentes: o PC é {VersaoPcFlow.App} e o celular é {versaoApp}."
                });
                Console.WriteLine($"VERSAO_INCOMPATIVEL {versaoApp}");
                return;
            }

            await EscreverAsync(ssl, new
            {
                tipo = "conectado",
                token = "token-de-teste",
                sessaoId = "sessao-de-teste",
                nome = "PC-DE-TESTE",
                maquinaId = "123456789",
                portaTela = 45457,
                monitores = new[] { "Monitor 1" },
                permissoes = new
                {
                    tela = true, entrada = true, clipboard = true,
                    energia = true, arquivos = true
                }
            });
            Console.WriteLine("CONECTADO_ENVIADO");

            // Ecoa os comandos recebidos para provar que a sessão fica utilizável.
            while (!encerrar.IsCancellationRequested)
            {
                var comando = await LerLinhaAsync(ssl, encerrar.Token);
                if (comando is null) break;
                Console.WriteLine($"COMANDO {comando}");
                using var cmd = JsonDocument.Parse(comando);
                if (cmd.RootElement.TryGetProperty("tipo", out var ct) && ct.GetString() == "ping")
                    await EscreverAsync(ssl, new { tipo = "pong", t = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
            }
            Console.WriteLine("SESSAO_ENCERRADA");
        }
    });
}

async Task EscreverAsync(Stream destino, object valor)
{
    var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(valor, json) + "\n");
    await destino.WriteAsync(bytes);
    await destino.FlushAsync();
}

static async Task<string?> LerLinhaAsync(Stream origem, CancellationToken ct)
{
    using var memoria = new MemoryStream();
    var buffer = new byte[1];
    while (memoria.Length < 131_072)
    {
        int lidos;
        try { lidos = await origem.ReadAsync(buffer, ct); }
        catch (Exception) { return null; }
        if (lidos == 0) return memoria.Length == 0 ? null : Encoding.UTF8.GetString(memoria.ToArray());
        if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(memoria.ToArray());
        if (buffer[0] != (byte)'\r') memoria.WriteByte(buffer[0]);
    }
    return null;
}

static X509Certificate2 CriarCertificado()
{
    using var rsa = RSA.Create(3072);
    var pedido = new CertificateRequest(
        "CN=PCFlow Local", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
    pedido.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
    pedido.CertificateExtensions.Add(new X509KeyUsageExtension(
        X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
    using var temporario = pedido.CreateSelfSigned(
        DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(5));
    return new X509Certificate2(temporario.Export(X509ContentType.Pfx));
}

// Espelha a constante do aplicativo para o teste de versão.
file static class VersaoPcFlow
{
    public const string App = "1.1.0";
}
