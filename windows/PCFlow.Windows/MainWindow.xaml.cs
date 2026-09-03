using PCFlow.Windows.Core;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net.NetworkInformation;
using System.Windows;
using System.Windows.Controls;
using Forms = System.Windows.Forms;
using WpfButton = System.Windows.Controls.Button;
using WpfMessageBox = System.Windows.MessageBox;

namespace PCFlow.Windows;

public partial class MainWindow : Window
{
    private readonly ServidorPcFlow _servidor = new();
    private readonly ServidorArquivosPcFlow _servidorArquivos = new();
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
        _servidorArquivos.Status += status => Dispatcher.Invoke(() => TextoStatus.Text = status);
        _servidor.DispositivosAlterados += () => Dispatcher.Invoke(AtualizarTela);
        _servidor.SessoesAlteradas += quantidade => Dispatcher.Invoke(() => AtualizarMoldura(quantidade));
        _servidor.SolicitarAceiteAsync = SolicitarAceiteAsync;
        _servidor.JanelaVisivel = () => Dispatcher.Invoke(() => IsVisible && WindowState != WindowState.Minimized);
        Loaded += async (_, _) =>
        {
            await _servidor.IniciarAsync();
            await _servidorArquivos.IniciarAsync();
            CarregarConfiguracaoNaTela();
            AtualizarTela();
            AtualizarDiagnostico();
        };
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
        AtualizarDiagnostico();
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

    private void AlternarBloqueio_Click(object sender, RoutedEventArgs e)
    {
        var id = (sender as WpfButton)?.Tag?.ToString();
        var dispositivo = _servidor.Configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (dispositivo is null) return;
        dispositivo.Bloqueado = !dispositivo.Bloqueado;
        _servidor.SalvarConfiguracao();
        AtualizarTela();
        TextoStatus.Text = dispositivo.Bloqueado ? $"{dispositivo.Nome} bloqueado" : $"{dispositivo.Nome} liberado";
    }

    private void RevogarDispositivo_Click(object sender, RoutedEventArgs e)
    {
        var id = (sender as WpfButton)?.Tag?.ToString();
        var dispositivo = _servidor.Configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (dispositivo is null) return;
        var confirmar = WpfMessageBox.Show(this,
            $"Revogar o acesso salvo de {dispositivo.Nome}?\n\nO dispositivo precisará ser autorizado novamente em uma próxima conexão.",
            "PCFlow — Revogar dispositivo", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No);
        if (confirmar != MessageBoxResult.Yes) return;
        _servidor.Configuracao.Dispositivos.Remove(dispositivo);
        _servidor.SalvarConfiguracao();
        AtualizarTela();
        TextoStatus.Text = $"Acesso de {dispositivo.Nome} revogado";
    }

    private void NavegarMenu_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not WpfButton botao) return;
        MarcarMenuSelecionado(botao);
        switch (botao.Tag?.ToString())
        {
            case "visao":
                ScrollPrincipal.ScrollToTop();
                SecaoVisaoGeral.BringIntoView();
                break;
            case "remoto":
                SecaoAcessoRemoto.BringIntoView();
                break;
            case "dispositivos":
                TituloDispositivos.BringIntoView();
                break;
            case "seguranca":
                ScrollSeguranca.ScrollToTop();
                ComboAcesso.Focus();
                PainelSeguranca.BorderBrush = FindResource("Destaque") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Goldenrod;
                break;
            case "recursos":
                SecaoRecursos.BringIntoView();
                AtualizarDiagnostico();
                break;
        }
    }

    private void MarcarMenuSelecionado(WpfButton selecionado)
    {
        var menus = new[] { MenuVisaoGeral, MenuAcessoRemoto, MenuDispositivos, MenuSeguranca, MenuRecursos };
        var destaque = FindResource("Destaque") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Goldenrod;
        var texto = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(246, 247, 249));
        var borda = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(59, 65, 74));
        foreach (var menu in menus)
        {
            menu.Foreground = menu == selecionado ? destaque : texto;
            menu.BorderBrush = menu == selecionado ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(108, 83, 35)) : borda;
        }
        if (selecionado != MenuSeguranca)
            PainelSeguranca.BorderBrush = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(42, 48, 56));
    }

    private void CopiarId_Click(object sender, RoutedEventArgs e)
    {
        System.Windows.Clipboard.SetText(_servidor.MaquinaId);
        TextoStatus.Text = "ID copiado";
    }

    private void CopiarEndereco_Click(object sender, RoutedEventArgs e)
    {
        System.Windows.Clipboard.SetText($"{_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}");
        TextoStatus.Text = "Endereço copiado";
    }

    private void CopiarPin_Click(object sender, RoutedEventArgs e)
    {
        System.Windows.Clipboard.SetText(_servidor.CodigoPareamento);
        TextoStatus.Text = "Código copiado";
    }

    private void AbrirDownloads_Click(object sender, RoutedEventArgs e)
    {
        var downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
        Directory.CreateDirectory(downloads);
        Process.Start(new ProcessStartInfo("explorer.exe", downloads) { UseShellExecute = true });
    }

    private void Diagnostico_Click(object sender, RoutedEventArgs e) => AtualizarDiagnostico();

    private void Atualizar_Click(object sender, RoutedEventArgs e)
    {
        AtualizarTela();
        CarregarConfiguracaoNaTela();
        AtualizarDiagnostico();
        TextoStatus.Text = _servidor.Pausado ? "Servidor pausado" : "Servidor ativo";
    }

    private void AtualizarDiagnostico()
    {
        try
        {
            var ip = IPGlobalProperties.GetIPGlobalProperties();
            var tcp = ip.GetActiveTcpListeners().Select(x => x.Port).ToHashSet();
            var udp = ip.GetActiveUdpListeners().Select(x => x.Port).ToHashSet();
            var controle = tcp.Contains(ServidorPcFlow.PortaControle) ? "OK" : "FECHADA";
            var tela = tcp.Contains(ServidorPcFlow.PortaTela) ? "OK" : "FECHADA";
            var arquivos = tcp.Contains(45458) ? "OK" : "FECHADA";
            var descoberta = udp.Contains(ServidorPcFlow.PortaDescoberta) ? "OK" : "FECHADA";
            TextoDiagnostico.Text = $"Servidor: {(_servidor.Ativo ? (_servidor.Pausado ? "pausado" : "ativo") : "inativo")} · Controle {ServidorPcFlow.PortaControle}: {controle} · Tela {ServidorPcFlow.PortaTela}: {tela} · Arquivos 45458: {arquivos} · Descoberta UDP {ServidorPcFlow.PortaDescoberta}: {descoberta}";
        }
        catch (Exception ex)
        {
            TextoDiagnostico.Text = $"Não foi possível concluir o diagnóstico: {ex.Message}";
        }
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
            _servidorArquivos.DisposeAsync().AsTask().GetAwaiter().GetResult();
            _servidor.DisposeAsync().AsTask().GetAwaiter().GetResult();
            System.Windows.Application.Current.Shutdown();
        }
        base.OnClosing(e);
    }

    private void Minimizar_Click(object sender, RoutedEventArgs e) => Hide();
    private void Pausar_Click(object sender, RoutedEventArgs e)
    {
        _servidor.AlternarPausa();
        BotaoPausar.Content = _servidor.Pausado ? "▶" : "Ⅱ";
        TextoStatus.Text = _servidor.Pausado ? "Servidor pausado" : "Servidor ativo";
        AtualizarDiagnostico();
    }
    private void NovoCodigo_Click(object sender, RoutedEventArgs e) { _servidor.GerarNovoCodigo(); AtualizarTela(); TextoStatus.Text = "Novo código gerado"; }
    private async void Encerrar_Click(object sender, RoutedEventArgs e) => await EncerrarAsync();
    private void Restaurar() { Show(); WindowState = WindowState.Normal; Activate(); }

    private async Task EncerrarAsync()
    {
        if (_encerrando) return;
        _encerrando = true;
        _moldura?.Close();
        _tray.Visible = false;
        _tray.Dispose();
        await _servidorArquivos.DisposeAsync();
        await _servidor.DisposeAsync();
        Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
    }
}
