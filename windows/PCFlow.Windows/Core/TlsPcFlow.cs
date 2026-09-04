using System.Net.Security;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;

namespace PCFlow.Windows.Core;

/// <summary>
/// Handshake TLS do servidor.
///
/// PROBLEMA CORRIGIDO (era o motivo de "connection closed" no celular):
/// o código anterior exigia <c>SslProtocols.Tls12 | SslProtocols.Tls13</c>.
/// No Windows 10 o SChannel não expõe TLS 1.3 — ele só existe a partir do
/// Windows 11 / Server 2022 — e pedir um protocolo indisponível faz o .NET
/// lançar na hora:
///
///     "Authentication failed because the platform does not support
///      the requested protocol"
///
/// O handshake morria antes de qualquer byte do PCFlow trafegar, então o
/// Android via apenas "connection closed" e o PC registrava "Falha TLS".
///
/// A correção é não fixar a versão: <see cref="SslProtocols.None"/> manda o
/// sistema negociar a melhor versão que ele realmente tem (recomendação da
/// própria Microsoft). Em Windows 11 isso continua fechando em TLS 1.3; em
/// Windows 10 fecha em TLS 1.2, que é seguro e suficiente para a LAN.
/// O fallback explícito cobre sistemas antigos com política restritiva.
/// </summary>
public static class TlsPcFlow
{
    /// <summary>
    /// Autentica como servidor tolerando diferenças de versão do Windows.
    /// Devolve a descrição do que foi negociado, para o diagnóstico.
    /// </summary>
    public static async Task<string> AutenticarServidorAsync(
        SslStream ssl, X509Certificate2 certificado, CancellationToken ct)
    {
        try
        {
            await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
            {
                ServerCertificate = certificado,
                ClientCertificateRequired = false,
                // None = "use o melhor que este Windows suporta".
                EnabledSslProtocols = SslProtocols.None
            }, ct).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is AuthenticationException or PlatformNotSupportedException
                                   or NotSupportedException or ArgumentException)
        {
            // Windows com política que rejeita a negociação automática:
            // insiste em TLS 1.2, que existe em todo Windows 10 ou superior.
            await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
            {
                ServerCertificate = certificado,
                ClientCertificateRequired = false,
                EnabledSslProtocols = SslProtocols.Tls12
            }, ct).ConfigureAwait(false);
        }

        return ssl.SslProtocol.ToString();
    }

    /// <summary>Transforma a falha técnica em algo que o usuário consegue agir.</summary>
    public static string ExplicarFalha(Exception ex)
    {
        var texto = ex.Message;
        if (texto.Contains("does not support", StringComparison.OrdinalIgnoreCase) ||
            ex is PlatformNotSupportedException)
        {
            return "Este Windows não aceitou a versão de TLS solicitada. " +
                   "Atualize o PCFlow para a versão 1.1.0 ou mais nova.";
        }
        if (texto.Contains("Cannot find the certificate", StringComparison.OrdinalIgnoreCase) ||
            texto.Contains("private key", StringComparison.OrdinalIgnoreCase))
        {
            return "A identidade TLS deste PC está corrompida. Feche o PCFlow, apague " +
                   "%AppData%\\PCFlow\\identidade.pfx e abra de novo.";
        }
        if (texto.Contains("received an unexpected", StringComparison.OrdinalIgnoreCase) ||
            texto.Contains("call to SSPI failed", StringComparison.OrdinalIgnoreCase))
        {
            return "O aplicativo do celular está em uma versão diferente da do PC. " +
                   "Instale o APK que acompanha esta versão do PCFlow.";
        }
        return texto;
    }
}
