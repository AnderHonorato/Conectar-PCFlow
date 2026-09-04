using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using PCFlow.Windows.Core;
using Xunit;

namespace PCFlow.Tests;

/// <summary>
/// Testes do handshake TLS.
///
/// POR QUE A CI NÃO PEGOU O DEFEITO ORIGINAL:
/// o runner "windows-latest" é Windows Server 2022, que suporta TLS 1.3. Exigir
/// <c>Tls12 | Tls13</c> funcionava lá e quebrava no Windows 10 do usuário, onde o
/// SChannel não expõe TLS 1.3. O smoke test antigo só provava que o handshake
/// funcionava na máquina da CI — nunca que funcionava numa máquina sem TLS 1.3.
///
/// Estes testes fecham essa lacuna: o caso decisivo é um cliente que só fala
/// TLS 1.2, exatamente como o Windows 10 se comporta.
/// </summary>
public class TestesTls
{
    private static X509Certificate2 CriarCertificado()
    {
        using var rsa = RSA.Create(2048);
        var pedido = new CertificateRequest(
            "CN=PCFlow Teste", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        pedido.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        pedido.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
        using var temporario = pedido.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(1));
        // Exporta/reimporta para garantir que a chave privada acompanha o certificado.
        return new X509Certificate2(temporario.Export(X509ContentType.Pfx));
    }

    /// <summary>Sobe um servidor de uma conexão só e devolve a porta usada.</summary>
    private static (Task<string> Servidor, int Porta) IniciarServidor(X509Certificate2 certificado)
    {
        var ouvinte = new TcpListener(IPAddress.Loopback, 0);
        ouvinte.Start();
        var porta = ((IPEndPoint)ouvinte.LocalEndpoint).Port;

        var tarefa = Task.Run(async () =>
        {
            using var cliente = await ouvinte.AcceptTcpClientAsync();
            ouvinte.Stop();
            using var ssl = new SslStream(cliente.GetStream(), false);
            var negociado = await TlsPcFlow.AutenticarServidorAsync(
                ssl, certificado, CancellationToken.None);

            // Ecoa uma linha, provando que o canal ficou realmente utilizável.
            var buffer = new byte[256];
            var lidos = await ssl.ReadAsync(buffer);
            await ssl.WriteAsync(Encoding.UTF8.GetBytes(
                Encoding.UTF8.GetString(buffer, 0, lidos).Trim() + "-ok\n"));
            await ssl.FlushAsync();
            return negociado;
        });

        return (tarefa, porta);
    }

    private static async Task<(string Resposta, SslProtocols Protocolo)> ConectarClienteAsync(
        int porta, SslProtocols protocolosDoCliente)
    {
        using var tcp = new TcpClient();
        await tcp.ConnectAsync(IPAddress.Loopback, porta);
        using var ssl = new SslStream(tcp.GetStream(), false,
            (_, _, _, _) => true); // pinagem é validada pelo app, não por CA

        await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
        {
            TargetHost = "PCFlow Teste",
            EnabledSslProtocols = protocolosDoCliente
        });

        await ssl.WriteAsync(Encoding.UTF8.GetBytes("ola\n"));
        await ssl.FlushAsync();
        var buffer = new byte[256];
        var lidos = await ssl.ReadAsync(buffer);
        return (Encoding.UTF8.GetString(buffer, 0, lidos).Trim(), ssl.SslProtocol);
    }

    [Fact]
    public async Task HandshakeFuncionaComNegociacaoAutomatica()
    {
        using var certificado = CriarCertificado();
        var (servidor, porta) = IniciarServidor(certificado);

        var (resposta, _) = await ConectarClienteAsync(porta, SslProtocols.None);

        Assert.Equal("ola-ok", resposta);
        Assert.False(string.IsNullOrWhiteSpace(await servidor));
    }

    /// <summary>
    /// ESTE É O TESTE QUE REPRODUZ O CASO DO WINDOWS 10.
    /// Um cliente limitado a TLS 1.2 representa o SChannel sem TLS 1.3.
    /// Com o código antigo o servidor sequer chegava a este ponto: ele estourava
    /// em AuthenticateAsServerAsync antes de aceitar qualquer cliente.
    /// </summary>
    [Fact]
    public async Task HandshakeFuncionaComClienteQueSoFalaTls12()
    {
        using var certificado = CriarCertificado();
        var (servidor, porta) = IniciarServidor(certificado);

        var (resposta, protocolo) = await ConectarClienteAsync(porta, SslProtocols.Tls12);

        Assert.Equal("ola-ok", resposta);
        Assert.Equal(SslProtocols.Tls12, protocolo);
        Assert.Equal("Tls12", await servidor);
    }

    [Fact]
    public async Task ServidorNaoExigeVersaoFixaDeTls()
    {
        // O servidor precisa aceitar tanto um cliente moderno quanto um antigo.
        using var certificado = CriarCertificado();

        foreach (var protocoloDoCliente in new[] { SslProtocols.None, SslProtocols.Tls12 })
        {
            var (servidor, porta) = IniciarServidor(certificado);
            var (resposta, _) = await ConectarClienteAsync(porta, protocoloDoCliente);
            Assert.Equal("ola-ok", resposta);
            await servidor;
        }
    }

    [Fact]
    public void FalhaDePlataformaViraOrientacaoAcionavel()
    {
        // Mensagem literal que apareceu na tela do usuário.
        var original = new AuthenticationException(
            "Authentication failed because the platform does not support the requested protocol");

        var explicado = TlsPcFlow.ExplicarFalha(original);

        Assert.Contains("Windows não aceitou", explicado);
        Assert.Contains("1.1.0", explicado);
        Assert.DoesNotContain("platform does not support", explicado);
    }

    [Fact]
    public void FalhaDeCertificadoOrientaApagarAIdentidade()
    {
        var explicado = TlsPcFlow.ExplicarFalha(
            new AuthenticationException("The credentials supplied to the package were not recognized: private key"));

        Assert.Contains("identidade.pfx", explicado);
    }

    [Fact]
    public void FalhaDesconhecidaPreservaAMensagemOriginal()
    {
        var explicado = TlsPcFlow.ExplicarFalha(new IOException("cabo desconectado"));
        Assert.Equal("cabo desconectado", explicado);
    }
}
