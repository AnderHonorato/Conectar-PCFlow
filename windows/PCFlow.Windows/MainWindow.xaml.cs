using PCFlow.Windows.Core;
using System.Collections.Concurrent;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net.NetworkInformation;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Forms = System.Windows.Forms;
using WpfButton = System.Windows.Controls.Button;
using WpfBrush = System.Windows.Media.Brush;
using WpfMessageBox = System.Windows.MessageBox;

namespace PCFlow.Windows;

public partial class MainWindow : Window
{
    private readonly ServidorPcFlow _servidor = new();
    private readonly ServidorArquivosPcFlow _servidorArquivos = new();
    private AgenteRelay? _relay;
    private List<PcRemoto> _pcsEncontrados = [];
    private readonly Forms.NotifyIcon _tray;
    private readonly ConcurrentQueue<string> _eventos = new();
    private MolduraSessaoWindow? _moldura;
    private bool _encerrando;
    private bool _carregado;
    private bool _aplicandoConfiguracao;
    private string _pagina = "visao";
    private bool? _firewallOk;
    private int _sessoesAtivas;

    public MainWindow()
    {
        InitializeComponent();
        AjustarParaTela();

        _tray = new Forms.NotifyIcon
        {
            Text = "PCFlow — acesso remoto local",
            Visible = true,
            Icon = System.Drawing.SystemIcons.Application,
            ContextMenuStrip = CriarMenuTray()
        };
        _tray.DoubleClick += (_, _) => Restaurar();

        // BeginInvoke: estes eventos chegam de threads do servidor. Com Invoke,
        // um evento durante o encerramento travaria a thread da interface.
        _servidor.StatusAlterado += status => Dispatcher.BeginInvoke(() => RegistrarEvento(status));
        _servidorArquivos.Status += status => Dispatcher.BeginInvoke(() => RegistrarEvento(status));
        _servidor.DispositivosAlterados += () => Dispatcher.BeginInvoke(AtualizarTela);
        _servidor.SessoesAlteradas += quantidade => Dispatcher.BeginInvoke(() =>
        {
            _sessoesAtivas = quantidade;
            AtualizarMoldura(quantidade);
            AtualizarStatusVisual();
        });
        _servidor.SolicitarAceiteAsync = SolicitarAceiteAsync;
        _servidor.JanelaVisivel = () => Dispatcher.Invoke(() => IsVisible && WindowState != WindowState.Minimized);

        Loaded += AoCarregar;
        SizeChanged += (_, _) => AjustarLayoutResponsivo();
    }

    // ------------------------------------------------------------------
    // Janela
    // ------------------------------------------------------------------

    /// <summary>
    /// A janela era fixa em 1280x800 com mínimo de 1040x680. Num notebook de
    /// 1366x768 com escala de 125% a área útil do WPF fica em torno de 1092x568:
    /// a janela nascia maior que a tela e o mínimo impedia encolher.
    /// Aqui ela é sempre limitada à área de trabalho real e recentralizada.
    /// </summary>
    private void AjustarParaTela()
    {
        var area = SystemParameters.WorkArea;
        if (area.Width <= 0 || area.Height <= 0) return;

        MinWidth = Math.Min(620, Math.Max(380, area.Width - 40));
        MinHeight = Math.Min(440, Math.Max(320, area.Height - 40));

        var largura = _servidor.Configuracao.JanelaLargura > 0 ? _servidor.Configuracao.JanelaLargura : 1100;
        var altura = _servidor.Configuracao.JanelaAltura > 0 ? _servidor.Configuracao.JanelaAltura : 720;

        Width = Math.Max(MinWidth, Math.Min(largura, area.Width - 20));
        Height = Math.Max(MinHeight, Math.Min(altura, area.Height - 20));
        Left = area.Left + Math.Max(0, (area.Width - Width) / 2);
        Top = area.Top + Math.Max(0, (area.Height - Height) / 2);
    }

    private void AjustarLayoutResponsivo()
    {
        if (!_carregado) return;
        var estreita = ActualWidth < 820;
        ColunaNav.Width = new GridLength(estreita ? 70 : 192);
        RotuloLogo.Visibility = estreita ? Visibility.Collapsed : Visibility.Visible;
        RodapeVersao.Visibility = estreita ? Visibility.Collapsed : Visibility.Visible;
        foreach (var b in Menus())
            b.HorizontalContentAlignment = estreita
                ? System.Windows.HorizontalAlignment.Center
                : System.Windows.HorizontalAlignment.Left;
        ColunaQr.Width = ActualWidth < 940 ? new GridLength(0) : GridLength.Auto;
    }

    private WpfButton[] Menus() =>
        [MenuVisaoGeral, MenuConexao, MenuAcessoRemoto, MenuInternet, MenuControlar,
         MenuDispositivos, MenuSeguranca, MenuRecursos];

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    private async void AoCarregar(object? remetente, RoutedEventArgs e)
    {
        _carregado = true;
        RodapeVersao.Text = $"v{VersaoPcFlow.App} · protocolo 2";

        await _servidor.IniciarAsync();
        await _servidorArquivos.IniciarAsync();

        // Primeira execução: cria a regra do firewall sem pedir nada.
        // Se o Windows recusar por falta de elevação, a página Conexão mostra o botão.
        GarantirFirewallSilencioso();

        CarregarConfiguracaoNaTela();
        AtualizarTela();
        SelecionarPagina("visao");
        AtualizarDiagnostico();
        AtualizarLog();
        AjustarLayoutResponsivo();
        AtualizarStatusVisual();

        // Religa sozinho o que o usuário já tinha deixado ligado.
        if (_servidor.Configuracao.UsarServidorRelay &&
            !string.IsNullOrWhiteSpace(_servidor.Configuracao.ServidorRelay))
            await LigarRelayAsync();

        if (_servidor.Configuracao.PermitirAcessoExterno)
            _ = _servidor.PrepararAcessoExternoAsync();
    }

    private void GarantirFirewallSilencioso()
    {
        Task.Run(() =>
        {
            var jaExiste = IntegracaoWindows.RegrasExistem();
            if (!jaExiste) IntegracaoWindows.GarantirRegrasFirewall(out var detalhe);
            var ok = IntegracaoWindows.RegrasExistem();
            Dispatcher.BeginInvoke(() =>
            {
                _firewallOk = ok;
                RegistrarEvento(ok
                    ? "Firewall: regras do PCFlow ativas para a rede privada."
                    : "Firewall: sem regra do PCFlow. Use Conexão → Liberar no firewall.");
                AtualizarInfoFirewall();
            });
        });
    }

    private Forms.ContextMenuStrip CriarMenuTray()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Abrir PCFlow", null, (_, _) => Restaurar());
        menu.Items.Add("Dispositivos", null, (_, _) => { Restaurar(); SelecionarPagina("dispositivos"); });
        menu.Items.Add("Conexão", null, (_, _) => { Restaurar(); SelecionarPagina("conexao"); });
        menu.Items.Add(new Forms.ToolStripSeparator());
        var pausar = new Forms.ToolStripMenuItem("Pausar servidor");
        pausar.Click += (_, _) =>
        {
            _servidor.AlternarPausa();
            pausar.Text = _servidor.Pausado ? "Retomar servidor" : "Pausar servidor";
            Dispatcher.BeginInvoke(AtualizarStatusVisual);
        };
        menu.Items.Add(pausar);
        menu.Items.Add("Reiniciar servidor", null, async (_, _) => await _servidor.ReiniciarAsync());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Sair", null, async (_, _) => await EncerrarAsync());
        return menu;
    }

    private Task<bool> SolicitarAceiteAsync(SolicitacaoConexao solicitacao)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            Restaurar();
            _tray.ShowBalloonTip(1500, "Solicitação de acesso",
                $"{solicitacao.Nome} quer controlar este computador.", Forms.ToolTipIcon.Info);
            var conhecido = solicitacao.DispositivoConhecido ? "Dispositivo já conhecido." : "Novo dispositivo.";
            return WpfMessageBox.Show(this,
                $"{solicitacao.Nome} ({solicitacao.EnderecoIp}) quer iniciar uma sessão remota.\n\n{conhecido}\n\nPermitir acesso?",
                "PCFlow — Solicitação de conexão", MessageBoxButton.YesNo,
                MessageBoxImage.Question, MessageBoxResult.No) == MessageBoxResult.Yes;
        }).Task;
    }

    // ------------------------------------------------------------------
    // Status e registro
    // ------------------------------------------------------------------

    private void RegistrarEvento(string mensagem)
    {
        TextoStatus.Text = mensagem;
        _eventos.Enqueue($"{DateTime.Now:HH:mm:ss}  {mensagem}");
        while (_eventos.Count > 300) _eventos.TryDequeue(out _);
        if (_pagina == "recursos") AtualizarLog();
        AtualizarStatusVisual();
    }

    private void AtualizarStatusVisual()
    {
        if (!_carregado) return;
        var cor = !_servidor.Ativo ? (WpfBrush)FindResource("Alerta")
            : _servidor.Pausado ? (WpfBrush)FindResource("Destaque")
            : (WpfBrush)FindResource("Sucesso");
        LuzStatus.Fill = cor;
        TextoStatus.Foreground = cor;
        BotaoPausar.Content = _servidor.Pausado ? "Retomar" : "Pausar";
        _tray.Text = _servidor.Ativo
            ? (_sessoesAtivas > 0 ? $"PCFlow — {_sessoesAtivas} sessão(ões)" : "PCFlow — aguardando")
            : "PCFlow — parado";
    }

    private void AtualizarLog()
        => ListaLog.ItemsSource = _eventos.Reverse().Take(200).ToList();

    // ------------------------------------------------------------------
    // Navegação
    // ------------------------------------------------------------------

    private void NavegarMenu_Click(object sender, RoutedEventArgs e)
    {
        if (sender is WpfButton botao) SelecionarPagina(botao.Tag?.ToString() ?? "visao");
    }

    private void SelecionarPagina(string pagina)
    {
        _pagina = pagina;
        PaginaVisao.Visibility = pagina == "visao" ? Visibility.Visible : Visibility.Collapsed;
        PaginaConexao.Visibility = pagina == "conexao" ? Visibility.Visible : Visibility.Collapsed;
        PaginaRemoto.Visibility = pagina == "remoto" ? Visibility.Visible : Visibility.Collapsed;
        PaginaInternet.Visibility = pagina == "internet" ? Visibility.Visible : Visibility.Collapsed;
        PaginaControlar.Visibility = pagina == "controlar" ? Visibility.Visible : Visibility.Collapsed;
        PaginaDispositivos.Visibility = pagina == "dispositivos" ? Visibility.Visible : Visibility.Collapsed;
        PaginaSeguranca.Visibility = pagina == "seguranca" ? Visibility.Visible : Visibility.Collapsed;
        PaginaRecursos.Visibility = pagina == "recursos" ? Visibility.Visible : Visibility.Collapsed;

        TituloPagina.Text = pagina switch
        {
            "conexao" => "Conexão",
            "remoto" => "Acesso remoto",
            "internet" => "Pela internet",
            "controlar" => "Controlar outro PC",
            "dispositivos" => "Dispositivos",
            "seguranca" => "Segurança",
            "recursos" => "Diagnóstico",
            _ => "Visão geral"
        };

        var destaque = (WpfBrush)FindResource("Destaque");
        var normal = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xC8, 0xCF, 0xD8));
        var bordaAtiva = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0x6C, 0x53, 0x23));
        foreach (var menu in Menus())
        {
            var ativo = (menu.Tag?.ToString() ?? "") == pagina;
            menu.Foreground = ativo ? destaque : normal;
            menu.BorderBrush = ativo ? bordaAtiva : System.Windows.Media.Brushes.Transparent;
        }

        ScrollPrincipal.ScrollToTop();
        if (pagina == "conexao") { AtualizarConexao(); AtualizarInfoFirewall(); }
        if (pagina == "internet") AtualizarInternet();
        if (pagina == "recursos") { AtualizarDiagnostico(); AtualizarLog(); }
    }

    // ------------------------------------------------------------------
    // Visão geral
    // ------------------------------------------------------------------

    private void AtualizarTela()
    {
        TextoMaquinaId.Text = FormatarId(_servidor.MaquinaId);
        TextoPin.Text = FormatarPin(_servidor.CodigoPareamento);
        TextoEndereco.Text = $"{Environment.MachineName} · {_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}";

        var lista = _servidor.Dispositivos.OrderByDescending(d => d.UltimaConexao).ToList();
        ListaDispositivos.ItemsSource = null;
        ListaDispositivos.ItemsSource = lista;
        AvisoSemDispositivos.Visibility = lista.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        var payload = $"pcflow://connect?host={Uri.EscapeDataString(_servidor.EnderecoLocal)}" +
                      $"&port={ServidorPcFlow.PortaControle}&id={_servidor.MaquinaId}" +
                      $"&pin={_servidor.CodigoPareamento}&tls={_servidor.ImpressaoTls}";
        ImagemQr.Source = QrCodeVisual.Criar(payload);
    }

    private void CopiarId_Click(object sender, RoutedEventArgs e)
    {
        System.Windows.Clipboard.SetText(_servidor.MaquinaId);
        RegistrarEvento("ID copiado para a área de transferência");
    }

    private void CopiarEndereco_Click(object sender, RoutedEventArgs e)
    {
        System.Windows.Clipboard.SetText($"{_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}");
        RegistrarEvento("Endereço copiado para a área de transferência");
    }

    private void NovoCodigo_Click(object sender, RoutedEventArgs e)
    {
        _servidor.GerarNovoCodigo();
        AtualizarTela();
        RegistrarEvento("Novo código de pareamento gerado");
    }

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

    private void AtualizarConexao()
    {
        if (!_carregado) return;
        try
        {
            var enderecos = NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                            n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .SelectMany(n => n.GetIPProperties().UnicastAddresses)
                .Where(a => a.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)
                .Select(a => a.Address.ToString())
                .Distinct().ToList();

            InfoRede.Text = enderecos.Count > 0
                ? "Endereços deste PC: " + string.Join(", ", enderecos) +
                  "\n\nO celular só precisa estar no mesmo Wi‑Fi. É normal e esperado que o último " +
                  "número do IP seja diferente do daqui — o PCFlow aceita qualquer endereço da rede local."
                : "Nenhuma rede ativa encontrada.";
        }
        catch (Exception ex)
        {
            InfoRede.Text = $"Não foi possível listar a rede: {ex.Message}";
        }

        InfoPortas.Text = $"Controle TCP {ServidorPcFlow.PortaControle} · Tela TCP {ServidorPcFlow.PortaTela} · " +
                          $"Arquivos TCP {ServidorArquivosPcFlow.Porta} · Descoberta UDP {ServidorPcFlow.PortaDescoberta}";
        InfoTls.Text = $"Identidade TLS desta máquina: {_servidor.ImpressaoTls[..16]}…";
    }

    private void AtualizarInfoFirewall(bool forcar = false)
    {
        if (_firewallOk is not null && !forcar)
        {
            InfoFirewall.Text = _firewallOk == true
                ? "Regras do PCFlow encontradas no firewall."
                : "Nenhuma regra do PCFlow encontrada. Use o botão abaixo.";
            return;
        }
        InfoFirewall.Text = "Verificando as regras do firewall…";
        Task.Run(() => IntegracaoWindows.RegrasExistem()).ContinueWith(t =>
        {
            _firewallOk = t.IsCompletedSuccessfully && t.Result;
            Dispatcher.BeginInvoke(() => AtualizarInfoFirewall());
        });
    }

    private void Firewall_Click(object sender, RoutedEventArgs e)
    {
        if (!IntegracaoWindows.EhAdministrador())
        {
            var r = WpfMessageBox.Show(this,
                "Para gravar a regra do firewall o PCFlow precisa ser aberto como administrador uma única vez.\n\n" +
                "Reabrir agora com permissão de administrador?",
                "Firewall", MessageBoxButton.YesNo, MessageBoxImage.Question);
            if (r != MessageBoxResult.Yes) return;
            if (IntegracaoWindows.ReabrirComoAdministrador("--firewall"))
            {
                _encerrando = true;
                Task.Run(async () =>
                {
                    await _servidorArquivos.DisposeAsync();
                    await _servidor.DisposeAsync();
                    Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
                });
                return;
            }
            WpfMessageBox.Show(this, "A elevação foi cancelada.", "Firewall",
                MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var ok = IntegracaoWindows.GarantirRegrasFirewall(out var detalhe);
        RegistrarEvento(detalhe);
        _firewallOk = null;
        AtualizarInfoFirewall(forcar: true);
        WpfMessageBox.Show(this, detalhe, "Firewall", MessageBoxButton.OK,
            ok ? MessageBoxImage.Information : MessageBoxImage.Warning);
    }

    private async void TestarPortas_Click(object sender, RoutedEventArgs e)
    {
        var mensagem = await Task.Run(() =>
        {
            string Testar(int porta)
            {
                try
                {
                    using var c = new System.Net.Sockets.TcpClient();
                    return c.ConnectAsync(System.Net.IPAddress.Loopback, porta).Wait(TimeSpan.FromSeconds(2))
                        ? "OK" : "sem resposta";
                }
                catch (Exception) { return "sem resposta"; }
            }
            return $"Controle {ServidorPcFlow.PortaControle}: {Testar(ServidorPcFlow.PortaControle)}\n" +
                   $"Tela {ServidorPcFlow.PortaTela}: {Testar(ServidorPcFlow.PortaTela)}\n" +
                   $"Arquivos {ServidorArquivosPcFlow.Porta}: {Testar(ServidorArquivosPcFlow.Porta)}\n\n" +
                   "Se todas estiverem OK e o celular ainda falhar, o bloqueio está no firewall " +
                   "ou os dois não estão no mesmo Wi‑Fi.";
        });
        WpfMessageBox.Show(this, mensagem, "Teste de portas", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private async void Reiniciar_Click(object sender, RoutedEventArgs e)
    {
        await _servidor.ReiniciarAsync();
        AtualizarConexao();
        AtualizarDiagnostico();
        AtualizarStatusVisual();
    }

    // ------------------------------------------------------------------
    // Configuração
    // ------------------------------------------------------------------

    private void CarregarConfiguracaoNaTela()
    {
        _aplicandoConfiguracao = true;
        var c = _servidor.Configuracao;
        ComboAcesso.SelectedIndex = c.AcessoInterativo switch { "janela" => 1, "nunca" => 2, _ => 0 };
        CheckTela.IsChecked = c.PermitirTela;
        CheckEntrada.IsChecked = c.PermitirEntrada;
        CheckClipboard.IsChecked = c.PermitirClipboard;
        CheckEnergia.IsChecked = c.PermitirEnergia;
        CheckArquivos.IsChecked = c.PermitirArquivos;
        CheckDescoberta.IsChecked = c.DescobertaRede;
        CheckMoldura.IsChecked = c.MolduraSessao;
        CheckMinimizar.IsChecked = c.MinimizarParaBandeja;
        _aplicandoConfiguracao = false;
    }

    /// <summary>Cada mudança é salva na hora — não existe mais "esqueci de salvar".</summary>
    private void Config_Click(object sender, RoutedEventArgs e) => SalvarConfiguracao();
    private void Config_Changed(object sender, SelectionChangedEventArgs e) => SalvarConfiguracao();

    private void SalvarConfiguracao()
    {
        if (!_carregado || _aplicandoConfiguracao) return;
        var c = _servidor.Configuracao;
        c.AcessoInterativo = (ComboAcesso.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "sempre";
        c.PermitirTela = CheckTela.IsChecked == true;
        c.PermitirEntrada = CheckEntrada.IsChecked == true;
        c.PermitirClipboard = CheckClipboard.IsChecked == true;
        c.PermitirEnergia = CheckEnergia.IsChecked == true;
        c.PermitirArquivos = CheckArquivos.IsChecked == true;
        c.DescobertaRede = CheckDescoberta.IsChecked == true;
        c.MolduraSessao = CheckMoldura.IsChecked == true;
        c.MinimizarParaBandeja = CheckMinimizar.IsChecked == true;
        _servidor.SalvarConfiguracao();
        RegistrarEvento("Configuração salva");
    }

    private void DefinirSenha_Click(object sender, RoutedEventArgs e)
    {
        if (!_servidor.DefinirSenhaNaoSupervisionada(CampoSenha.Password))
        {
            WpfMessageBox.Show(this, "Use uma senha com pelo menos 8 caracteres.", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        CampoSenha.Clear();
        RegistrarEvento("Senha de acesso não supervisionado definida");
        WpfMessageBox.Show(this, "Acesso não supervisionado ativado com senha.", "PCFlow",
            MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void RemoverSenha_Click(object sender, RoutedEventArgs e)
    {
        _servidor.RemoverSenhaNaoSupervisionada();
        CampoSenha.Clear();
        RegistrarEvento("Senha de acesso não supervisionado removida");
        WpfMessageBox.Show(this, "Senha removida. As conexões voltam a depender das regras de acesso.",
            "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    // ------------------------------------------------------------------
    // Dispositivos
    // ------------------------------------------------------------------

    private void AlternarBloqueio_Click(object sender, RoutedEventArgs e)
    {
        var id = (sender as WpfButton)?.Tag?.ToString();
        var dispositivo = _servidor.Configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (dispositivo is null) return;
        dispositivo.Bloqueado = !dispositivo.Bloqueado;
        _servidor.SalvarConfiguracao();
        AtualizarTela();
        RegistrarEvento(dispositivo.Bloqueado ? $"{dispositivo.Nome} bloqueado" : $"{dispositivo.Nome} liberado");
    }

    private void RevogarDispositivo_Click(object sender, RoutedEventArgs e)
    {
        var id = (sender as WpfButton)?.Tag?.ToString();
        var dispositivo = _servidor.Configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (dispositivo is null) return;
        var confirmar = WpfMessageBox.Show(this,
            $"Revogar o acesso salvo de {dispositivo.Nome}?\n\nEle precisará ser autorizado de novo na próxima conexão.",
            "PCFlow — Revogar dispositivo", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No);
        if (confirmar != MessageBoxResult.Yes) return;
        _servidor.Configuracao.Dispositivos.Remove(dispositivo);
        _servidor.SalvarConfiguracao();
        AtualizarTela();
        RegistrarEvento($"Acesso de {dispositivo.Nome} revogado");
    }


    // ------------------------------------------------------------------
    // Pela internet
    // ------------------------------------------------------------------

    private void AtualizarInternet()
    {
        if (!_carregado) return;
        _aplicandoConfiguracao = true;
        var c = _servidor.Configuracao;
        CheckAcessoExterno.IsChecked = c.PermitirAcessoExterno;
        CheckUpnp.IsChecked = c.AbrirPortasUpnp;
        CheckUsarRelay.IsChecked = c.UsarServidorRelay;
        if (string.IsNullOrEmpty(CampoServidorRelay.Text)) CampoServidorRelay.Text = c.ServidorRelay;
        _aplicandoConfiguracao = false;

        // Acesso externo sem senha definida não conecta ninguém: é melhor
        // dizer isso aqui do que deixar o usuário tentando do celular.
        AvisoSenhaExterna.Visibility = c.PermitirAcessoExterno && string.IsNullOrEmpty(c.SenhaHash)
            ? Visibility.Visible : Visibility.Collapsed;

        CampoCodigoAcesso.Text = _servidor.CodigoAcessoExterno is { Length: > 0 } codigo
            ? codigo
            : _servidor.CodigoAcessoLocal;

        InfoRelay.Text = _relay is null
            ? (string.IsNullOrWhiteSpace(c.ServidorRelay)
                ? "Nenhum servidor configurado. Sem ele, o acesso de fora depende do roteador aceitar UPnP."
                : $"Servidor {c.ServidorRelay} salvo, mas desligado.")
            : $"{_relay.UltimoDetalhe}\nCódigo neste servidor: {_relay.CodigoServidor}";
    }

    private void ConfigInternet_Click(object sender, RoutedEventArgs e)
    {
        if (!_carregado || _aplicandoConfiguracao) return;
        var c = _servidor.Configuracao;
        c.PermitirAcessoExterno = CheckAcessoExterno.IsChecked == true;
        c.AbrirPortasUpnp = CheckUpnp.IsChecked == true;
        c.UsarServidorRelay = CheckUsarRelay.IsChecked == true;
        _servidor.SalvarConfiguracao();
        AtualizarInternet();
        RegistrarEvento(c.PermitirAcessoExterno
            ? "Acesso de fora da rede local liberado"
            : "Acesso de fora da rede local bloqueado");
    }

    private async void PrepararInternet_Click(object sender, RoutedEventArgs e)
    {
        BotaoAbrirInternet.IsEnabled = false;
        InfoInternet.Text = "Procurando o roteador e conferindo o IP público…";
        try
        {
            var resultado = await _servidor.PrepararAcessoExternoAsync();
            InfoInternet.Text = resultado.Detalhe;
            CampoCodigoAcesso.Text = _servidor.CodigoAcessoExterno is { Length: > 0 } codigo
                ? codigo : _servidor.CodigoAcessoLocal;

            if (resultado.Cgnat)
                WpfMessageBox.Show(this,
                    resultado.Detalhe + "\n\nUse a seção Servidor de retransmissão logo abaixo.",
                    "PCFlow — CGNAT detectado", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        catch (Exception ex)
        {
            InfoInternet.Text = $"Falhou: {ex.Message}";
        }
        finally { BotaoAbrirInternet.IsEnabled = true; }
    }

    private async void FecharInternet_Click(object sender, RoutedEventArgs e)
    {
        await _servidor.FecharAcessoExternoAsync();
        InfoInternet.Text = "Portas removidas do roteador.";
        RegistrarEvento("Encaminhamento de portas removido do roteador");
    }

    private void CopiarCodigoAcesso_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(CampoCodigoAcesso.Text) || CampoCodigoAcesso.Text == "—") return;
        System.Windows.Clipboard.SetText(CampoCodigoAcesso.Text);
        RegistrarEvento("Código de acesso copiado");
    }

    private void CodigoLocal_Click(object sender, RoutedEventArgs e)
    {
        CampoCodigoAcesso.Text = _servidor.CodigoAcessoLocal;
        RegistrarEvento("Mostrando o código da rede local");
    }

    private async void SalvarRelay_Click(object sender, RoutedEventArgs e)
    {
        var c = _servidor.Configuracao;
        c.ServidorRelay = CampoServidorRelay.Text.Trim();
        c.UsarServidorRelay = true;
        CheckUsarRelay.IsChecked = true;
        _servidor.SalvarConfiguracao();

        if (string.IsNullOrWhiteSpace(c.ServidorRelay))
        {
            InfoRelay.Text = "Informe o endereço do servidor primeiro.";
            return;
        }

        await LigarRelayAsync();
        AtualizarInternet();
    }

    private async Task LigarRelayAsync()
    {
        if (_relay is not null) { await _relay.PararAsync(); _relay = null; }
        _relay = new AgenteRelay(_servidor);
        _relay.Status += mensagem => Dispatcher.BeginInvoke(() =>
        {
            RegistrarEvento(mensagem);
            if (_pagina == "internet") AtualizarInternet();
        });
        _relay.Iniciar();
    }

    private async void DesligarRelay_Click(object sender, RoutedEventArgs e)
    {
        if (_relay is not null) { await _relay.PararAsync(); _relay = null; }
        _servidor.Configuracao.UsarServidorRelay = false;
        CheckUsarRelay.IsChecked = false;
        _servidor.SalvarConfiguracao();
        AtualizarInternet();
    }

    // ------------------------------------------------------------------
    // Controlar outro PC
    // ------------------------------------------------------------------

    private async void ProcurarPcs_Click(object sender, RoutedEventArgs e)
    {
        InfoBusca.Text = "Procurando computadores com PCFlow na rede…";
        ListaPcs.ItemsSource = null;
        _pcsEncontrados = (await ClientePcFlow.DescobrirAsync()).ToList();
        ListaPcs.ItemsSource = _pcsEncontrados;
        InfoBusca.Text = _pcsEncontrados.Count switch
        {
            0 => "Nenhum outro PC encontrado. Confira se o PCFlow está aberto no outro computador e se os dois estão na mesma rede.",
            1 => "1 computador encontrado.",
            var n => $"{n} computadores encontrados."
        };
    }

    private void ConectarPc_Click(object sender, RoutedEventArgs e)
    {
        var id = (sender as WpfButton)?.Tag?.ToString();
        var pc = _pcsEncontrados.FirstOrDefault(p => p.MaquinaId == id);
        if (pc is null) return;
        AbrirControleRemoto(pc, null, null);
    }

    private void ConectarPorCodigo_Click(object sender, RoutedEventArgs e)
    {
        var destino = ClientePcFlow.DoCodigo(CampoCodigoDestino.Text);
        if (destino is null)
        {
            WpfMessageBox.Show(this,
                "Não reconheci esse código. Copie de novo, na página Pela internet do outro computador.",
                "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        AbrirControleRemoto(destino,
            string.IsNullOrWhiteSpace(CampoPinDestino.Text) ? null : CampoPinDestino.Text,
            string.IsNullOrEmpty(CampoSenhaDestino.Password) ? null : CampoSenhaDestino.Password);
    }

    private void AbrirControleRemoto(PcRemoto destino, string? pin, string? senha)
    {
        var janela = new ControleRemotoWindow(destino, pin, senha) { Owner = this };
        janela.Show();
        RegistrarEvento($"Abrindo controle remoto de {destino.Nome}");
    }

    // ------------------------------------------------------------------
    // Permissões por dispositivo
    // ------------------------------------------------------------------

    private void PermissaoDispositivo_Click(object sender, RoutedEventArgs e)
    {
        // O binding TwoWay já escreveu no objeto; aqui só persiste e avisa.
        if (sender is not System.Windows.Controls.CheckBox caixa) return;
        var id = caixa.Tag?.ToString();
        var dispositivo = _servidor.Configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (dispositivo is null) return;
        _servidor.SalvarConfiguracao();
        AtualizarTela();
        RegistrarEvento($"Permissões de {dispositivo.Nome} atualizadas");
    }

    // ------------------------------------------------------------------
    // Diagnóstico
    // ------------------------------------------------------------------

    private void Diagnostico_Click(object sender, RoutedEventArgs e) { AtualizarDiagnostico(); AtualizarLog(); }

    private void Atualizar_Click(object sender, RoutedEventArgs e)
    {
        AtualizarTela();
        CarregarConfiguracaoNaTela();
        AtualizarConexao();
        AtualizarDiagnostico();
        AtualizarStatusVisual();
    }

    private void AtualizarDiagnostico()
    {
        try
        {
            var ip = IPGlobalProperties.GetIPGlobalProperties();
            var tcp = ip.GetActiveTcpListeners().Select(x => x.Port).ToHashSet();
            var udp = ip.GetActiveUdpListeners().Select(x => x.Port).ToHashSet();
            string E(bool aberta) => aberta ? "OK" : "FECHADA";
            TextoDiagnostico.Text =
                $"Servidor: {(_servidor.Ativo ? (_servidor.Pausado ? "pausado" : "ativo") : "inativo")}\n" +
                $"Sessões ativas: {_sessoesAtivas} · Dispositivos autorizados: {_servidor.Dispositivos.Count}\n" +
                $"Controle {ServidorPcFlow.PortaControle}: {E(tcp.Contains(ServidorPcFlow.PortaControle))} · " +
                $"Tela {ServidorPcFlow.PortaTela}: {E(tcp.Contains(ServidorPcFlow.PortaTela))} · " +
                $"Arquivos {ServidorArquivosPcFlow.Porta}: {E(tcp.Contains(ServidorArquivosPcFlow.Porta))} · " +
                $"Descoberta UDP {ServidorPcFlow.PortaDescoberta}: {E(udp.Contains(ServidorPcFlow.PortaDescoberta))}\n" +
                $"PCFlow v{VersaoPcFlow.App} · protocolo 2 · {Environment.OSVersion}";
        }
        catch (Exception ex)
        {
            TextoDiagnostico.Text = $"Não foi possível concluir o diagnóstico: {ex.Message}";
        }
    }

    private void Exportar_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var pasta = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PCFlow");
            Directory.CreateDirectory(pasta);
            var arquivo = Path.Combine(pasta, $"pcflow-diagnostico-{DateTime.Now:yyyyMMdd-HHmmss}.txt");
            var conteudo = new List<string>
            {
                $"PCFlow {VersaoPcFlow.App} — protocolo 2",
                $"Máquina: {Environment.MachineName}   SO: {Environment.OSVersion}",
                $"Endereço: {_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}",
                $"Identidade TLS: {_servidor.ImpressaoTls}",
                TextoDiagnostico.Text,
                new string('-', 60)
            };
            conteudo.AddRange(_eventos);
            File.WriteAllLines(arquivo, conteudo);
            WpfMessageBox.Show(this, $"Diagnóstico salvo em:\n{arquivo}", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            WpfMessageBox.Show(this, $"Não foi possível exportar: {ex.Message}", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void AbrirDownloads_Click(object sender, RoutedEventArgs e)
    {
        var downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
        Directory.CreateDirectory(downloads);
        Process.Start(new ProcessStartInfo("explorer.exe", downloads) { UseShellExecute = true });
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

    // ------------------------------------------------------------------
    // Encerramento
    // ------------------------------------------------------------------

    protected override void OnClosing(CancelEventArgs e)
    {
        if (_encerrando) { base.OnClosing(e); return; }
        GuardarTamanhoJanela();

        if (_servidor.Configuracao.MinimizarParaBandeja)
        {
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(1200, "PCFlow continua ativo",
                "O servidor segue disponível na bandeja.", Forms.ToolTipIcon.Info);
            return;
        }

        _encerrando = true;
        _tray.Visible = false;
        _tray.Dispose();
        try { _servidorArquivos.DisposeAsync().AsTask().Wait(TimeSpan.FromSeconds(3)); } catch (Exception) { }
        try { _servidor.DisposeAsync().AsTask().Wait(TimeSpan.FromSeconds(3)); } catch (Exception) { }
        System.Windows.Application.Current.Shutdown();
        base.OnClosing(e);
    }

    private void GuardarTamanhoJanela()
    {
        if (WindowState != WindowState.Normal) return;
        _servidor.Configuracao.JanelaLargura = ActualWidth;
        _servidor.Configuracao.JanelaAltura = ActualHeight;
        _servidor.SalvarConfiguracao();
    }

    private void Minimizar_Click(object sender, RoutedEventArgs e) { GuardarTamanhoJanela(); Hide(); }

    private void Pausar_Click(object sender, RoutedEventArgs e)
    {
        _servidor.AlternarPausa();
        AtualizarStatusVisual();
        AtualizarDiagnostico();
    }

    private async void Encerrar_Click(object sender, RoutedEventArgs e) => await EncerrarAsync();

    private void Restaurar()
    {
        Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        AjustarParaTela();
        Activate();
        Topmost = true;
        Topmost = false;
    }

    private async Task EncerrarAsync()
    {
        if (_encerrando) return;
        _encerrando = true;
        _moldura?.Close();
        _tray.Visible = false;
        _tray.Dispose();
        if (_relay is not null) await _relay.DisposeAsync();
        await _servidorArquivos.DisposeAsync();
        await _servidor.DisposeAsync();
        Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
    }
}
