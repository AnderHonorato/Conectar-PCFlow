using PCFlow.Windows.Core;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace PCFlow.Windows;

public sealed class JanelaTesteRemotoV13 : Window
{
    private readonly int _monitor;
    private readonly int _qualidade;
    private readonly Image _imagem = new() { Stretch = Stretch.Uniform };
    private readonly TextBlock _status = new() { Foreground = Brushes.Gainsboro, Margin = new Thickness(12, 6, 12, 10) };
    private readonly DispatcherTimer _timer;

    public JanelaTesteRemotoV13(int monitor, int fps, string qualidade)
    {
        _monitor = Math.Clamp(monitor, 0, Math.Max(0, CapturaTela.QuantidadeMonitores - 1));
        _qualidade = qualidade switch { "alta" => 84, "equilibrada" => 68, "economica" => 48, _ => 72 };
        Title = "PCFlow V1.3 — Sessão de teste";
        Width = 1040;
        Height = 680;
        MinWidth = 720;
        MinHeight = 480;
        Background = new SolidColorBrush(Color.FromRgb(8, 11, 15));
        WindowStartupLocation = WindowStartupLocation.CenterScreen;

        var grid = new Grid();
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(52) });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var topo = new Border
        {
            Background = new SolidColorBrush(Color.FromRgb(20, 27, 34)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(48, 58, 69)),
            BorderThickness = new Thickness(0, 0, 0, 1),
            Child = new TextBlock
            {
                Text = $"PCFlow · Prévia local do Monitor {_monitor + 1}",
                Foreground = new SolidColorBrush(Color.FromRgb(243, 177, 63)),
                FontSize = 16,
                FontWeight = FontWeights.SemiBold,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(16, 0, 0, 0)
            }
        };
        Grid.SetRow(topo, 0);
        grid.Children.Add(topo);

        var fundoImagem = new Border { Background = Brushes.Black, Child = _imagem, Padding = new Thickness(8) };
        Grid.SetRow(fundoImagem, 1);
        grid.Children.Add(fundoImagem);
        Grid.SetRow(_status, 2);
        grid.Children.Add(_status);
        Content = grid;

        var intervalo = Math.Clamp(1000 / Math.Max(1, fps), 40, 250);
        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(intervalo) };
        _timer.Tick += (_, _) => Atualizar();
        Loaded += (_, _) => { Atualizar(); _timer.Start(); };
        Closed += (_, _) => _timer.Stop();
    }

    private void Atualizar()
    {
        try
        {
            var bytes = CapturaTela.CapturarJpeg(_monitor, _qualidade);
            if (bytes.Length == 0) return;
            using var ms = new MemoryStream(bytes);
            var imagem = new BitmapImage();
            imagem.BeginInit();
            imagem.CacheOption = BitmapCacheOption.OnLoad;
            imagem.StreamSource = ms;
            imagem.EndInit();
            imagem.Freeze();
            _imagem.Source = imagem;
            _status.Text = $"Prévia atualizada · {imagem.PixelWidth} × {imagem.PixelHeight} · qualidade {_qualidade}%";
        }
        catch (Exception ex)
        {
            _status.Text = $"Não foi possível capturar a tela: {ex.Message}";
        }
    }
}
