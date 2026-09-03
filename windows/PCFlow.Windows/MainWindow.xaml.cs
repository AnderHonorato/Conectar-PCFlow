using PCFlow.Windows.Core;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using Forms = System.Windows.Forms;
using WpfMessageBox = System.Windows.MessageBox;

namespace PCFlow.Windows;

public partial class MainWindow : Window
{
    private readonly ServidorPcFlow _servidor = new();
    private readonly Forms.NotifyIcon _tray;
    private MolduraSessaoWindow? _moldura;
    private bool _encerrando;

    public MainWindow()
    {
        InitializeComponent();
        _tray = new Forms.NotifyIcon
        {
            Text = "PCFlow — acesso remoto local",
            Visible = true,
            Icon = System.Drawing.SystemIcons.Application,
            ContextMenuStrip = CriarMenuTray()
        };
        _tray.DoubleClick += (_, _) => Restaurar();
        _servidor.StatusAlterado += status => Dispatcher.Invoke(() => TextoStatus.Text = status);
        _servidor.DispositivosAlterados += () => Dispatcher.Invoke(AtualizarTela);
        _servidor.SessoesAlteradas += quantidade => Dispatcher.Invoke(() => AtualizarMoldura(quantidade));
        _servidor.SolicitarAceiteAsync = SolicitarAceiteAsync;
        _servidor.JanelaVisivel = () => Dispatcher.Invoke(() => IsVisible && WindowState != WindowState.Minimized);
        Loaded += async (_, _) => { await _servidor.IniciarAsync(); CarregarConfiguracaoNaTela(); AtualizarTela(); };
    }

    private Forms.ContextMenuStrip CriarMenuTray()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Abrir PCFlow", null, (_, _) => Restaurar());
        menu.Items.Add("Sair", null, async (_, _) => await EncerrarAsync());
        return menu;
    }

    private Task<bool> SolicitarAceiteAsync(SolicitacaoConexao solicitacao)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            Restaurar();
            _tray.ShowBalloonTip(1500, "Solicitação de acesso", $"{solicitacao.Nome} quer controlar este computador.", Forms.ToolTipIcon.Info);
            var conhecido = solicitacao.DispositivoConhecido ? "Dispositivo já conhecido." : "Novo dispositivo.";
            return WpfMessageBox.Show(this,
                $"{solicitacao.Nome} ({solicitacao.EnderecoIp}) quer iniciar uma sessão remota.\n\n{conhecido}\n\nPermitir acesso?",
                "PCFlow — Solicitação de conexão", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No) == MessageBoxResult.Yes;
        }).Task;
    }

    private void AtualizarTela()
    {
        TextoMaquinaId.Text = FormatarId(_servidor.MaquinaId);
        TextoPin.Text = FormatarPin(_servidor.CodigoPareamento);
        TextoEndereco.Text = $"{Environment.MachineName} · {_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}";
        ListaDispositivos.ItemsSource = null;
        ListaDispositivos.ItemsSource = _servidor.Dispositivos.OrderByDescending(d => d.UltimaConexao).ToList();
        var payload = $"pcflow://connect?host={Uri.EscapeDataString(_servidor.EnderecoLocal)}&port={ServidorPcFlow.PortaControle}&id={_servidor.MaquinaId}&pin={_servidor.CodigoPareamento}&tls={_servidor.ImpressaoTls}";
        ImagemQr.Source = QrCodeVisual.Criar(payload);
    }

    private void CarregarConfiguracaoNaTela()
    {
        var c = _servidor.Configuracao;
        ComboAcesso.SelectedIndex = c.AcessoInterativo switch { "janela" => 1, "nunca" => 2, _ => 0 };
        CheckTela.IsChecked = c.PermitirTela;
        CheckEntrada.IsChecked = c.PermitirEntrada;
        CheckClipboard.IsChecked = c.PermitirClipboard;
        CheckEnergia.IsChecked = c.PermitirEnergia;
        CheckArquivos.IsChecked = c.PermitirArquivos;
        CheckDescoberta.IsChecked = c.DescobertaRede;
        CheckMoldura.IsChecked = c.MolduraSessao;
    }

    private void SalvarSeguranca_Click(object sender, RoutedEventArgs e)
    {
        var c = _servidor.Configuracao;
        c.AcessoInterativo = (ComboAcesso.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "sempre";
        c.PermitirTela = CheckTela.IsChecked == true;
        c.PermitirEntrada = CheckEntrada.IsChecked == true;
        c.PermitirClipboard = CheckClipboard.IsChecked == true;
        c.PermitirEnergia = CheckEnergia.IsChecked == true;
        c.PermitirArquivos = CheckArquivos.IsChecked == true;
        c.DescobertaRede = CheckDescoberta.IsChecked == true;
        c.MolduraSessao = CheckMoldura.IsChecked == true;
        _servidor.SalvarConfiguracao();
        WpfMessageBox.Show(this, "Configurações salvas.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void DefinirSenha_Click(object sender, RoutedEventArgs e)
    {
        if (!_servidor.DefinirSenhaNaoSupervisionada(CampoSenha.Password))
        {
            WpfMessageBox.Show(this, "Use uma senha com pelo menos 8 caracteres.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        CampoSenha.Clear();
        WpfMessageBox.Show(this, "Acesso não supervisionado ativado com senha.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void RemoverSenha_Click(object sender, RoutedEventArgs e)
    {
        _servidor.RemoverSenhaNaoSupervisionada();
        CampoSenha.Clear();
        WpfMessageBox.Show(this, "Senha removida. Conexões voltarão a depender das regras de acesso interativo.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void AtualizarMoldura(int sessoes)
    {
        if (sessoes > 0 && _servidor.Configuracao.MolduraSessao)
        {
            _moldura ??= new MolduraSessaoWindow();
            if (!_moldura.IsVisible) _moldura.Show();
        }
        else
        {
            _moldura?.Close();
            _moldura = null;
        }
    }

    private static string FormatarId(string id) => id.Length == 9 ? $"{id[..3]} {id.Substring(3, 3)} {id[6..]}" : id;
    private static string FormatarPin(string pin) => pin.Length == 6 ? $"{pin[..3]} {pin[3..]}" : pin;

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_encerrando && _servidor.Configuracao.MinimizarParaBandeja)
        {
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(1200, "PCFlow continua ativo", "O servidor segue disponível na bandeja.", Forms.ToolTipIcon.Info);
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
    private void Pausar_Click(object sender, RoutedEventArgs e) { _servidor.AlternarPausa(); BotaoPausar.Content = _servidor.Pausado ? "▶" : "Ⅱ"; }
    private void NovoCodigo_Click(object sender, RoutedEventArgs e) { _servidor.GerarNovoCodigo(); AtualizarTela(); }
    private void Restaurar() { Show(); WindowState = WindowState.Normal; Activate(); }

    private async Task EncerrarAsync()
    {
        _encerrando = true;
        _moldura?.Close();
        _tray.Visible = false;
        _tray.Dispose();
        await _servidor.DisposeAsync();
        Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
    }
}
