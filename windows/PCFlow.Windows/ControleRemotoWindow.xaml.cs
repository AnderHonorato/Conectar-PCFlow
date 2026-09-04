using System.IO;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using PCFlow.Windows.Core;
using Point = System.Windows.Point;
using MouseEventArgs = System.Windows.Input.MouseEventArgs;
using KeyEventArgs = System.Windows.Input.KeyEventArgs;

namespace PCFlow.Windows;

/// <summary>
/// A tela do outro computador dentro do Windows: mesma ideia do celular, com
/// mouse e teclado de verdade. Recebe os quadros JPEG do <see cref="ClientePcFlow"/>
/// e devolve posição, cliques, rolagem e teclas.
/// </summary>
public partial class ControleRemotoWindow : Window
{
    private readonly ClientePcFlow _cliente = new();
    private readonly PcRemoto _destino;
    private readonly string? _pin;
    private readonly string? _senha;
    private bool _tecladoAtivo;
    private bool _encerrando;
    private int _monitor;

    public ControleRemotoWindow(PcRemoto destino, string? pin, string? senha)
    {
        InitializeComponent();
        _destino = destino;
        _pin = pin;
        _senha = senha;

        _cliente.QuadroRecebido += DesenharQuadro;
        _cliente.Status += mensagem => Dispatcher.BeginInvoke(() => TextoStatus.Text = mensagem);
        _cliente.Encerrado += Encerrar;

        Loaded += async (_, _) => await ConectarAsync();
        Closed += async (_, _) => { _encerrando = true; await _cliente.DisposeAsync(); };
        PreviewKeyDown += Janela_KeyDown;
        PreviewTextInput += Janela_TextInput;
    }

    private async Task ConectarAsync()
    {
        TextoTitulo.Text = $"Conectando a {_destino.Nome}…";
        var ok = await _cliente.ConectarAsync(_destino, _pin, _senha);
        if (!ok) return;

        TextoTitulo.Text = _cliente.NomeRemoto;
        ComboMonitor.Items.Clear();
        for (var i = 0; i < _cliente.QuantidadeMonitores; i++) ComboMonitor.Items.Add($"Monitor {i + 1}");
        ComboMonitor.SelectedIndex = 0;
        ComboMonitor.Visibility = _cliente.QuantidadeMonitores > 1 ? Visibility.Visible : Visibility.Collapsed;

        if (!_cliente.Permissoes.Tela)
            AvisoTela.Text = "O outro computador não está permitindo exibir a tela.";
        if (!_cliente.Permissoes.Entrada)
            TextoDica.Text = "Somente visualização: o outro computador não liberou teclado e mouse.";
    }

    private void DesenharQuadro(byte[] jpeg) => Dispatcher.BeginInvoke(() =>
    {
        if (_encerrando) return;
        try
        {
            var imagem = new BitmapImage();
            imagem.BeginInit();
            imagem.StreamSource = new MemoryStream(jpeg);
            imagem.CacheOption = BitmapCacheOption.OnLoad;
            imagem.EndInit();
            imagem.Freeze();
            ImagemTela.Source = imagem;
            AvisoTela.Visibility = Visibility.Collapsed;
        }
        catch (Exception) { /* quadro corrompido: o próximo vem em milissegundos */ }
    });

    private void Encerrar(string motivo) => Dispatcher.BeginInvoke(() =>
    {
        if (_encerrando) return;
        TextoStatus.Text = motivo;
        AvisoTela.Text = motivo;
        AvisoTela.Visibility = Visibility.Visible;
    });

    // ---------- mouse ----------

    /// <summary>
    /// Converte o ponto do mouse nesta janela para a coordenada 0..1 da tela
    /// remota, descontando as bordas pretas que o Stretch="Uniform" cria quando
    /// as proporções das duas telas são diferentes.
    /// </summary>
    private (double X, double Y)? Normalizar(Point ponto)
    {
        if (ImagemTela.Source is not { } fonte) return null;
        var area = AreaTela.RenderSize;
        if (area.Width <= 0 || area.Height <= 0) return null;

        var escala = Math.Min(area.Width / fonte.Width, area.Height / fonte.Height);
        var largura = fonte.Width * escala;
        var altura = fonte.Height * escala;
        var esquerda = (area.Width - largura) / 2;
        var topo = (area.Height - altura) / 2;

        var x = (ponto.X - esquerda) / largura;
        var y = (ponto.Y - topo) / altura;
        if (x is < 0 or > 1 || y is < 0 or > 1) return null;
        return (x, y);
    }

    private void Tela_MouseMove(object sender, MouseEventArgs e)
    {
        if (!_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        if (Normalizar(e.GetPosition(AreaTela)) is not { } ponto) return;
        _cliente.Posicionar(ponto.X, ponto.Y, _monitor);
    }

    private void Tela_MouseDown(object sender, MouseButtonEventArgs e)
    {
        AreaTela.Focus();
        if (!_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        if (Normalizar(e.GetPosition(AreaTela)) is { } ponto) _cliente.Posicionar(ponto.X, ponto.Y, _monitor);
        _cliente.BotaoPressionado(e.ChangedButton == MouseButton.Right ? "direito" : "esquerdo");
    }

    private void Tela_MouseUp(object sender, MouseButtonEventArgs e)
    {
        if (!_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        _cliente.BotaoSolto(e.ChangedButton == MouseButton.Right ? "direito" : "esquerdo");
    }

    private void Tela_MouseWheel(object sender, MouseWheelEventArgs e)
    {
        if (!_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        _cliente.Rolar(e.Delta);
    }

    // ---------- teclado ----------

    private void Teclado_Click(object sender, RoutedEventArgs e)
    {
        _tecladoAtivo = !_tecladoAtivo;
        BotaoTeclado.Content = _tecladoAtivo ? "Teclado ligado" : "Teclado";
        TextoDica.Text = _tecladoAtivo
            ? "Teclado ligado: tudo o que você digitar nesta janela vai para o outro computador."
            : "Mova o mouse sobre a tela para controlar. O botão Teclado captura o que você digitar aqui.";
        AreaTela.Focus();
    }

    private void Janela_TextInput(object sender, TextCompositionEventArgs e)
    {
        if (!_tecladoAtivo || !_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        if (string.IsNullOrEmpty(e.Text)) return;
        _cliente.EnviarTexto(e.Text);
        e.Handled = true;
    }

    private void Janela_KeyDown(object sender, KeyEventArgs e)
    {
        if (!_tecladoAtivo || !_cliente.Conectado || !_cliente.Permissoes.Entrada) return;
        var tecla = TraduzirTecla(e.Key);
        if (tecla is null) return;
        _cliente.EnviarTecla(tecla);
        e.Handled = true;
    }

    /// <summary>Teclas que não geram texto e por isso precisam do nome combinado com o servidor.</summary>
    private static string? TraduzirTecla(Key tecla) => tecla switch
    {
        Key.Enter => "ENTER",
        Key.Back => "BACKSPACE",
        Key.Tab => "TAB",
        Key.Escape => "ESC",
        Key.Delete => "DELETE",
        Key.Home => "HOME",
        Key.End => "END",
        Key.Up => "UP",
        Key.Down => "DOWN",
        Key.Left => "LEFT",
        Key.Right => "RIGHT",
        Key.LWin or Key.RWin => "WIN",
        _ => null
    };

    private void CtrlAltDel_Click(object sender, RoutedEventArgs e)
    {
        if (_cliente.Conectado) _cliente.EnviarTecla("CTRL_ALT_DEL");
    }

    private async void Monitor_Changed(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (ComboMonitor.SelectedIndex < 0 || !_cliente.Conectado) return;
        _monitor = ComboMonitor.SelectedIndex;
        ImagemTela.Source = null;
        AvisoTela.Visibility = Visibility.Visible;
        await _cliente.TrocarMonitorAsync(_destino, _monitor);
    }

    private void Desconectar_Click(object sender, RoutedEventArgs e) => Close();
}
