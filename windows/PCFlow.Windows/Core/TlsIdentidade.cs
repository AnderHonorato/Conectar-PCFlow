using System.IO;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace PCFlow.Windows.Core;

public sealed class TlsIdentidade
{
    public X509Certificate2 Certificado { get; }
    public string ImpressaoDigital { get; }

    public TlsIdentidade()
    {
        var pasta = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PCFlow");
        Directory.CreateDirectory(pasta);
        var arquivo = Path.Combine(pasta, "identidade.pfx");

        if (!File.Exists(arquivo))
            CriarIdentidade(arquivo);

        Certificado = CarregarParaSchannel(arquivo);

        // Caso um arquivo antigo/corrompido não contenha mais a chave privada,
        // recria a identidade em vez de iniciar um servidor TLS impossível de usar.
        if (!Certificado.HasPrivateKey)
        {
            Certificado.Dispose();
            try { File.Delete(arquivo); } catch { }
            CriarIdentidade(arquivo);
            Certificado = CarregarParaSchannel(arquivo);
        }

        ImpressaoDigital = Convert.ToHexString(SHA256.HashData(Certificado.RawData)).ToLowerInvariant();
    }

    private static X509Certificate2 CarregarParaSchannel(string arquivo)
    {
        // IMPORTANTE: não usar EphemeralKeySet aqui.
        // No Windows, SslStream usa SChannel e algumas versões/plataformas não
        // conseguem autenticar como servidor quando a chave privada é efêmera.
        // UserKeySet + PersistKeySet cria um contêiner de chave utilizável pelo
        // SChannel enquanto o PFX continua sendo a fonte persistente do PCFlow.
        return new X509Certificate2(
            File.ReadAllBytes(arquivo),
            (string?)null,
            X509KeyStorageFlags.UserKeySet |
            X509KeyStorageFlags.PersistKeySet |
            X509KeyStorageFlags.Exportable);
    }

    private static void CriarIdentidade(string arquivo)
    {
        using var rsa = RSA.Create(3072);
        var requisicao = new CertificateRequest(
            "CN=PCFlow Local",
            rsa,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);

        requisicao.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        requisicao.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
            false));
        requisicao.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(requisicao.PublicKey, false));

        using var temporario = requisicao.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddDays(-1),
            DateTimeOffset.UtcNow.AddYears(5));

        var pfx = temporario.Export(X509ContentType.Pfx);
        File.WriteAllBytes(arquivo, pfx);
    }
}
