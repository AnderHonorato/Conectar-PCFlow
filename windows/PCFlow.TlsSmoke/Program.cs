using PCFlow.Windows.Core;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

var identidade = new TlsIdentidade();
if (!identidade.Certificado.HasPrivateKey)
    throw new Exception("Certificado PCFlow sem chave privada.");

var listener = new TcpListener(IPAddress.Loopback, 0);
listener.Start();
var porta = ((IPEndPoint)listener.LocalEndpoint).Port;

var servidor = Task.Run(async () =>
{
    using var tcp = await listener.AcceptTcpClientAsync();
    using var ssl = new SslStream(tcp.GetStream(), false);
    await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
    {
        ServerCertificate = identidade.Certificado,
        ClientCertificateRequired = false,
        EnabledSslProtocols = SslProtocols.Tls12
    });
    await ssl.WriteAsync(new byte[] { 0x50, 0x43, 0x46 });
    await ssl.FlushAsync();
});

using var clienteTcp = new TcpClient();
await clienteTcp.ConnectAsync(IPAddress.Loopback, porta);
using var clienteSsl = new SslStream(clienteTcp.GetStream(), false, (_, cert, _, _) =>
{
    if (cert is null) return false;
    using var c2 = new X509Certificate2(cert);
    var atual = Convert.ToHexString(SHA256.HashData(c2.RawData)).ToLowerInvariant();
    return CryptographicOperations.FixedTimeEquals(
        Convert.FromHexString(atual),
        Convert.FromHexString(identidade.ImpressaoDigital));
});

await clienteSsl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
{
    TargetHost = "PCFlow Local",
    EnabledSslProtocols = SslProtocols.Tls12,
    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
});

var buffer = new byte[3];
var lidos = 0;
while (lidos < buffer.Length)
{
    var n = await clienteSsl.ReadAsync(buffer.AsMemory(lidos));
    if (n == 0) throw new Exception("Servidor TLS encerrou o stream antes do teste.");
    lidos += n;
}

await servidor;
listener.Stop();

if (buffer[0] != 0x50 || buffer[1] != 0x43 || buffer[2] != 0x46)
    throw new Exception("Handshake TLS ocorreu, mas o canal não transportou dados corretamente.");

Console.WriteLine($"PCFlow TLS smoke test OK — {clienteSsl.SslProtocol} — chave persistente utilizável pelo SChannel.");
