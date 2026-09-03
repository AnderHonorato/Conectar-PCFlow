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

        if (File.Exists(arquivo))
        {
            Certificado = new X509Certificate2(File.ReadAllBytes(arquivo), (string?)null,
                X509KeyStorageFlags.Exportable | X509KeyStorageFlags.EphemeralKeySet);
        }
        else
        {
            using var rsa = RSA.Create(3072);
            var requisicao = new CertificateRequest("CN=PCFlow Local", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            requisicao.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
            requisicao.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
            using var temporario = requisicao.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(5));
            var pfx = temporario.Export(X509ContentType.Pfx);
            File.WriteAllBytes(arquivo, pfx);
            Certificado = new X509Certificate2(pfx, (string?)null,
                X509KeyStorageFlags.Exportable | X509KeyStorageFlags.EphemeralKeySet);
        }

        ImpressaoDigital = Convert.ToHexString(SHA256.HashData(Certificado.RawData)).ToLowerInvariant();
    }
}
