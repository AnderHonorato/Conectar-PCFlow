using System.IO;
using System.Windows.Media.Imaging;
using QRCoder;

namespace PCFlow.Windows;

/// <summary>
/// QR de pareamento. O conteúdo é uma URL pcflow:// com host, porta, PIN e nome,
/// exatamente o que o app Android espera ao escanear.
/// </summary>
public static class GeradorQr
{
    public static BitmapImage? Gerar(string conteudo, int lado)
    {
        if (string.IsNullOrWhiteSpace(conteudo)) return null;

        using var gerador = new QRCodeGenerator();
        using var dados = gerador.CreateQrCode(conteudo, QRCodeGenerator.ECCLevel.M);
        var png = new PngByteQRCode(dados);
        // O tamanho do módulo é escolhido para o QR chegar perto do lado pedido.
        var modulos = Math.Max(1, dados.ModuleMatrix.Count);
        var pixelsPorModulo = Math.Max(3, lado / modulos);
        var bytes = png.GetGraphic(pixelsPorModulo);

        var imagem = new BitmapImage();
        using var memoria = new MemoryStream(bytes);
        imagem.BeginInit();
        imagem.CacheOption = BitmapCacheOption.OnLoad;
        imagem.StreamSource = memoria;
        imagem.EndInit();
        imagem.Freeze();
        return imagem;
    }
}
