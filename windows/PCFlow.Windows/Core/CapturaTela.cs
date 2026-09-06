#if WINDOWS
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using Forms = System.Windows.Forms;
#endif

namespace PCFlow.Windows.Core;

/// <summary>
/// Conta de redimensionamento do quadro. Fica fora do <c>#if WINDOWS</c> de
/// propósito: é a única parte da captura que dá para testar sem uma tela real.
/// </summary>
public static class EscalaCaptura
{
    /// <summary>Abaixo disso a tela vira borrão e não dá para ler nada.</summary>
    public const int LarguraMinima = 160;

    /// <summary>
    /// Tamanho de saída para uma tela de <paramref name="larguraTela"/> x
    /// <paramref name="alturaTela"/>. Mantém a proporção e nunca amplia:
    /// esticar o quadro só gastaria banda inventando pixel.
    /// </summary>
    public static (int Largura, int Altura) Calcular(int larguraTela, int alturaTela, int larguraMaxima)
    {
        if (larguraTela <= 0 || alturaTela <= 0) return (0, 0);
        if (larguraMaxima <= 0 || larguraMaxima >= larguraTela) return (larguraTela, alturaTela);

        var largura = Math.Max(LarguraMinima, larguraMaxima);
        if (largura >= larguraTela) return (larguraTela, alturaTela);

        var altura = (int)Math.Round(alturaTela * (double)largura / larguraTela, MidpointRounding.AwayFromZero);
        return (largura, Math.Max(1, altura));
    }
}

#if WINDOWS
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

    /// <summary>
    /// Um quadro em JPEG. O vetor devolvido é novo a cada chamada — é a única
    /// alocação por quadro; bitmap, Graphics, buffer e codec são reaproveitados.
    /// </summary>
    /// <param name="larguraMaxima">0 mantém o tamanho nativo da tela.</param>
    public static byte[] CapturarJpeg(int monitor, int qualidade, int larguraMaxima = 0)
    {
        var telas = Forms.Screen.AllScreens;
        if (telas.Length == 0) return [];
        monitor = Math.Clamp(monitor, 0, telas.Length - 1);
        qualidade = Math.Clamp(qualidade, 30, 90);

        var limites = telas[monitor].Bounds;
        var (largura, altura) = EscalaCaptura.Calcular(limites.Width, limites.Height, larguraMaxima);
        if (largura <= 0 || altura <= 0) return [];

        var recurso = Obter(monitor, limites, largura, altura);

        // A mesma tela pode estar sendo transmitida para dois celulares ao mesmo
        // tempo; os recursos reaproveitados não aguentam dois quadros de uma vez.
        lock (recurso)
        {
            recurso.GraficoOrigem.CopyFromScreen(limites.Left, limites.Top, 0, 0, limites.Size, CopyPixelOperation.SourceCopy);

            var imagem = recurso.Origem;
            if (recurso.Destino is not null && recurso.GraficoDestino is not null)
            {
                recurso.GraficoDestino.DrawImage(recurso.Origem,
                    new Rectangle(0, 0, largura, altura),
                    new Rectangle(0, 0, limites.Width, limites.Height),
                    GraphicsUnit.Pixel);
                imagem = recurso.Destino;
            }

            recurso.Buffer.SetLength(0);
            imagem.Save(recurso.Buffer, Codec, recurso.ParametrosPara(qualidade));
            return recurso.Buffer.ToArray();
        }
    }

    private const int RecursosMaximos = 4;
    private static readonly TimeSpan Ociosidade = TimeSpan.FromSeconds(10);
    private static readonly ImageCodecInfo Codec = ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);
    private static readonly Dictionary<(int Monitor, int Largura), RecursoCaptura> Recursos = [];
    private static readonly object Trava = new();

    private static RecursoCaptura Obter(int monitor, Rectangle limites, int largura, int altura)
    {
        // Trava sempre antes da trava do recurso; o caminho inverso não existe,
        // então não há como travar de vez.
        lock (Trava)
        {
            var chave = (monitor, largura);
            if (Recursos.TryGetValue(chave, out var atual))
            {
                if (atual.Serve(limites, altura))
                {
                    atual.UltimoUso = DateTime.UtcNow;
                    return atual;
                }
                Recursos.Remove(chave);
                Descartar(atual);
            }

            var novo = new RecursoCaptura(limites, largura, altura);
            Recursos[chave] = novo;
            Faxina();
            return novo;
        }
    }

    private static void Faxina()
    {
        if (Recursos.Count <= RecursosMaximos) return;
        var corte = DateTime.UtcNow - Ociosidade;
        foreach (var chave in Recursos.Where(p => p.Value.UltimoUso < corte).Select(p => p.Key).ToArray())
        {
            var velho = Recursos[chave];
            Recursos.Remove(chave);
            Descartar(velho);
        }
    }

    /// <summary>Espera o quadro em andamento terminar antes de soltar o que ele usa.</summary>
    private static void Descartar(RecursoCaptura recurso)
    {
        lock (recurso) recurso.Dispose();
    }

    private sealed class RecursoCaptura : IDisposable
    {
        private readonly Rectangle _limites;
        private readonly int _altura;
        private EncoderParameters? _parametros;
        private int _qualidade;

        public RecursoCaptura(Rectangle limites, int largura, int altura)
        {
            _limites = limites;
            _altura = altura;
            Origem = new Bitmap(limites.Width, limites.Height, PixelFormat.Format24bppRgb);
            GraficoOrigem = Graphics.FromImage(Origem);

            if (largura == limites.Width && altura == limites.Height) return;

            Destino = new Bitmap(largura, altura, PixelFormat.Format24bppRgb);
            GraficoDestino = Graphics.FromImage(Destino);
            // Bilinear simples serrilha texto quando a redução passa de duas
            // vezes; o bicúbico de alta qualidade custa quase o dobro do tempo
            // sem diferença visível em tela de computador.
            GraficoDestino.InterpolationMode = InterpolationMode.HighQualityBilinear;
            GraficoDestino.PixelOffsetMode = PixelOffsetMode.HighQuality;
            GraficoDestino.SmoothingMode = SmoothingMode.None;
            GraficoDestino.CompositingMode = CompositingMode.SourceCopy;
            GraficoDestino.CompositingQuality = CompositingQuality.HighSpeed;
        }

        public Bitmap Origem { get; private set; }
        public Graphics GraficoOrigem { get; private set; }
        public Bitmap? Destino { get; private set; }
        public Graphics? GraficoDestino { get; private set; }
        public MemoryStream Buffer { get; } = new(256 * 1024);
        public DateTime UltimoUso { get; set; } = DateTime.UtcNow;

        /// <summary>Trocar de resolução ou arrastar a janela para outro monitor invalida o que está guardado.</summary>
        public bool Serve(Rectangle limites, int altura) => _limites == limites && _altura == altura;

        public EncoderParameters ParametrosPara(int qualidade)
        {
            if (_parametros is not null && _qualidade == qualidade) return _parametros;
            _parametros?.Dispose();
            _qualidade = qualidade;
            _parametros = new EncoderParameters(1);
            _parametros.Param[0] = new EncoderParameter(Encoder.Quality, (long)qualidade);
            return _parametros;
        }

        public void Dispose()
        {
            GraficoDestino?.Dispose();
            Destino?.Dispose();
            GraficoOrigem.Dispose();
            Origem.Dispose();
            _parametros?.Dispose();
            Buffer.Dispose();
            GraficoDestino = null;
            Destino = null;
        }
    }
}
#endif
