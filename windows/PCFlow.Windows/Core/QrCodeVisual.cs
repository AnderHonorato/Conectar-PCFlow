using System.IO;
using System.Windows.Media.Imaging;
using QRCoder;

namespace PCFlow.Windows.Core;

public static class QrCodeVisual
{
    public static BitmapImage Criar(string conteudo)
    {
        using var gerador = new QRCodeGenerator();
        using var dados = gerador.CreateQrCode(conteudo, QRCodeGenerator.ECCLevel.Q);
        var png = new PngByteQRCode(dados).GetGraphic(
            6,
            new byte[] { 20, 24, 30, 255 },
            new byte[] { 245, 247, 250, 255 });
        using var ms = new MemoryStream(png);
        var imagem = new BitmapImage();
        imagem.BeginInit();
        imagem.CacheOption = BitmapCacheOption.OnLoad;
        imagem.StreamSource = ms;
        imagem.EndInit();
        imagem.Freeze();
        return imagem;
    }
}
