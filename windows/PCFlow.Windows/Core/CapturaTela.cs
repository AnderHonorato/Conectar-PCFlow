using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using Forms = System.Windows.Forms;

namespace PCFlow.Windows.Core;

public static class CapturaTela
{
    public static int QuantidadeMonitores => Forms.Screen.AllScreens.Length;

    public static object[] DescreverMonitores() => Forms.Screen.AllScreens
        .Select((tela, indice) => new
        {
            indice,
            nome = tela.DeviceName,
            largura = tela.Bounds.Width,
            altura = tela.Bounds.Height,
            principal = tela.Primary
        }).Cast<object>().ToArray();

    public static byte[] CapturarJpeg(int monitor, int qualidade)
    {
        var telas = Forms.Screen.AllScreens;
        if (telas.Length == 0) return [];
        monitor = Math.Clamp(monitor, 0, telas.Length - 1);
        qualidade = Math.Clamp(qualidade, 30, 90);
        var bounds = telas[monitor].Bounds;

        using var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format24bppRgb);
        using (var graphics = Graphics.FromImage(bitmap))
            graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);

        using var ms = new MemoryStream();
        var codec = ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);
        using var parametros = new EncoderParameters(1);
        parametros.Param[0] = new EncoderParameter(Encoder.Quality, (long)qualidade);
        bitmap.Save(ms, codec, parametros);
        return ms.ToArray();
    }
}
