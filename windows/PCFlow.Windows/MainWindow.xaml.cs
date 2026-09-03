using PCFlow.Windows.Core;
using System.ComponentModel;
using System.Windows;
using Forms = System.Windows.Forms;

namespace PCFlow.Windows;

public partial class MainWindow : Window
{
    private readonly ServidorPcFlow _servidor = new();
    private readonly Forms.NotifyIcon _tray;
    private bool _encerrando;

    public MainWindow()
    {
        InitializeComponent();
        NomePc.Text = Environment.MachineName;
        _tray = new Forms.NotifyIcon
        {
            Text = "PCFlow — servidor local",
            Visible = true,
            Icon = System.Drawing.SystemIcons.Application,
            ContextMenuStrip = CriarMenuTray()
        };
        _tray.DoubleClick += (_, _) => Restaurar();
        _servidor.StatusAlterado += status => Dispatcher.Invoke(() => TextoStatus.Text = status);
        _servidor.DispositivosAlterados += () => Dispatcher.Invoke(AtualizarTela);
        Loaded += async (_, _) => { await _servidor.IniciarAsync(); CheckMinimizar.IsChecked = _servidor.Configuracao.MinimizarParaBandeja; AtualizarTela(); };
    }

    private Forms.ContextMenuStrip CriarMenuTray()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Abrir PCFlow", null, (_, _) => Restaurar());
        menu.Items.Add("Sair", null, async (_, _) => await EncerrarAsync());
        return menu;
    }

    private void AtualizarTela()
    {
        TextoPin.Text = FormatarPin(_servidor.CodigoPareamento);
        TextoEndereco.Text = $"{_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}";
        ListaDispositivos.ItemsSource = null;
        ListaDispositivos.ItemsSource = _servidor.Dispositivos.OrderByDescending(d => d.UltimaConexao).ToList();
    }

    private static string FormatarPin(string pin) => pin.Length == 6 ? $"{pin[..3]} {pin[3..]}" : pin;

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_encerrando && _servidor.Configuracao.MinimizarParaBandeja)
        {
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(1200, "PCFlow continua ativo", "O servidor foi minimizado para a bandeja.", Forms.ToolTipIcon.Info);
        }
        else if (!_encerrando)
        {
            _encerrando = true;
            _tray.Visible = false;
            _tray.Dispose();
            _servidor.DisposeAsync().AsTask().GetAwaiter().GetResult();
            System.Windows.Application.Current.Shutdown();
        }
        base.OnClosing(e);
    }

    private void Minimizar_Click(object sender, RoutedEventArgs e) => Hide();
    private void NovoPareamento_Click(object sender, RoutedEventArgs e) => AtualizarTela();
    private void Pausar_Click(object sender, RoutedEventArgs e)
    {
        _servidor.AlternarPausa();
        BotaoPausar.Content = _servidor.Pausado ? "▶" : "Ⅱ";
    }

    private void MinimizarAlterado(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded) return;
        _servidor.DefinirMinimizarParaBandeja(CheckMinimizar.IsChecked == true);
    }

    private void Restaurar() { Show(); WindowState = WindowState.Normal; Activate(); }

    private async Task EncerrarAsync()
    {
        _encerrando = true;
        _tray.Visible = false;
        _tray.Dispose();
        await _servidor.DisposeAsync();
        Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
    }
}
