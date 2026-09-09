using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using PCFlow.Windows.Tema;
using WpfWindow = System.Windows.Window;

namespace PCFlow.Windows.Dialogos;

/// <summary>
/// Cobertura escura por trás dos diálogos próprios.
///
/// O véu é a própria janela do diálogo esticada sobre a janela dona: assim o
/// escurecimento acompanha o diálogo ao abrir e fechar, e não sobra uma segunda
/// janela para gerenciar.
/// </summary>
internal static class Cortina
{
    public static void Posicionar(WpfWindow janela, WpfWindow? dono)
    {
        var area = SystemParameters.WorkArea;

        // Janela dona maximizada tem Left/Top sem relação com a tela: nesse caso
        // o véu cobre a área de trabalho inteira, que é o que o usuário enxerga.
        var usarDono = dono is not null && dono.IsVisible &&
                       dono.WindowState == WindowState.Normal &&
                       dono.ActualWidth > 200 && dono.ActualHeight > 200;

        if (usarDono && dono is not null)
        {
            janela.Left = dono.Left;
            janela.Top = dono.Top;
            janela.Width = dono.ActualWidth;
            janela.Height = dono.ActualHeight;
        }
        else
        {
            janela.Left = area.Left;
            janela.Top = area.Top;
            janela.Width = area.Width;
            janela.Height = area.Height;
        }
    }

    /// <summary>Abre em 200 ms com fade e leve subida, ou instantâneo quando o Windows pede menos animação.</summary>
    public static void Abrir(WpfWindow janela, TranslateTransform subida)
    {
        var duracao = GerenciadorTema.DuracaoDialogo;
        if (duracao <= TimeSpan.Zero)
        {
            janela.Opacity = 1;
            subida.Y = 0;
            return;
        }

        janela.Opacity = 0;
        var suavizacao = new CubicEase { EasingMode = EasingMode.EaseOut };
        janela.BeginAnimation(UIElement.OpacityProperty,
            new DoubleAnimation(0, 1, new Duration(duracao)) { EasingFunction = suavizacao });
        subida.BeginAnimation(TranslateTransform.YProperty,
            new DoubleAnimation(8, 0, new Duration(duracao)) { EasingFunction = suavizacao });
    }
}
