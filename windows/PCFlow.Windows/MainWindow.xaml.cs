using System.ComponentModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using PCFlow.Core;
using PCFlow.Windows.Plataforma;
using Forms = System.Windows.Forms;

namespace PCFlow.Windows;

public partial class MainWindow : Window
{
    private readonly ServidorPcFlow _servidor;
    private readonly Forms.NotifyIcon _bandeja;
    private readonly System.Windows.Threading.DispatcherTimer _relogio;
    private bool _encerrando;
    private bool _carregado;
    private int _pagina;

    public MainWindow(ServidorPcFlow servidor)
    {
        _servidor = servidor;
        InitializeComponent();

        AjustarParaTela();

        _bandeja = new Forms.NotifyIcon
        {
            Text = "PCFlow",
            Visible = true,
            Icon = System.Drawing.SystemIcons.Application,
            ContextMenuStrip = MontarMenuBandeja()
        };
        _bandeja.DoubleClick += (_, _) => Restaurar();

        // BeginInvoke e não Invoke: estes eventos chegam de threads do servidor.
        // Com Invoke, um evento disparado durante o encerramento (quando a thread
        // da interface está bloqueada esperando o servidor parar) travava o app.
        _servidor.EstadoAlterado += e => Dispatcher.BeginInvoke(() => AplicarEstado(e));
        _servidor.DispositivosAlterados += () => Dispatcher.BeginInvoke(AtualizarDispositivos);
        _servidor.Log.Adicionado += _ => Dispatcher.BeginInvoke(AtualizarLog);
        _servidor.AutorizarNovoDispositivo = PerguntarAutorizacao;

        _relogio = new System.Windows.Threading.DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(1)
        };
        _relogio.Tick += (_, _) => AtualizarPin(false);

        Loaded += AoCarregar;
        SizeChanged += (_, _) => AjustarLayoutResponsivo();
    }

    // ------------------------------------------------------------------
    // Dimensionamento
    // ------------------------------------------------------------------

    /// <summary>
    /// A versão anterior fixava 1180x760 com mínimo de 960x640. Em notebooks de
    /// 1366x768 com escala de 125% a área útil do WPF fica em torno de 1092x568,
    /// então a janela nascia maior que a tela e o mínimo impedia encolher.
    /// Aqui a janela é sempre limitada à área de trabalho real e recentralizada.
    /// </summary>
    private void AjustarParaTela()
    {
        var area = SystemParameters.WorkArea;
        if (area.Width <= 0 || area.Height <= 0) return;

        // Nunca exigir mais do que a tela oferece.
        MinWidth = Math.Min(600, Math.Max(360, area.Width - 40));
        MinHeight = Math.Min(420, Math.Max(300, area.Height - 40));

        var larguraDesejada = _servidor.Configuracao.JanelaLargura > 0
            ? _servidor.Configuracao.JanelaLargura : 1020;
        var alturaDesejada = _servidor.Configuracao.JanelaAltura > 0
            ? _servidor.Configuracao.JanelaAltura : 680;

        Width = Math.Max(MinWidth, Math.Min(larguraDesejada, area.Width - 20));
        Height = Math.Max(MinHeight, Math.Min(alturaDesejada, area.Height - 20));

        Left = area.Left + Math.Max(0, (area.Width - Width) / 2);
        Top = area.Top + Math.Max(0, (area.Height - Height) / 2);

        MaxWidth = double.PositiveInfinity;
        MaxHeight = double.PositiveInfinity;
    }

    /// <summary>Esconde rótulos e o QR quando a janela fica estreita, em vez de estourar o layout.</summary>
    private void AjustarLayoutResponsivo()
    {
        if (!_carregado) return;
        var estreita = ActualWidth < 780;
        ColunaNav.Width = new GridLength(estreita ? 64 : 176);
        RotuloLogo.Visibility = estreita ? Visibility.Collapsed : Visibility.Visible;
        RodapeVersao.Visibility = estreita ? Visibility.Collapsed : Visibility.Visible;

        foreach (var b in new[] { NavInicio, NavDispositivos, NavConexao, NavDiagnostico, NavAjustes })
            b.HorizontalContentAlignment = estreita
                ? System.Windows.HorizontalAlignment.Center
                : System.Windows.HorizontalAlignment.Left;

        ColunaQr.Width = ActualWidth < 900 ? new GridLength(0) : GridLength.Auto;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    private void AoCarregar(object? remetente, RoutedEventArgs e)
    {
        _carregado = true;
        var cfg = _servidor.Configuracao;

        NomePc.Text = _servidor.NomeMaquina;
        RodapeVersao.Text = $"v{Protocolo.VersaoApp} · protocolo {Protocolo.Versao}";
        InfoSobre.Text = $"PCFlow {Protocolo.VersaoApp}\nProtocolo v{Protocolo.Versao}\n" +
                         $"Configuração: {new ArmazenamentoConfiguracao().Caminho}\n" +
                         "Funciona sem conta, sem nuvem e sem anúncios.";

        ComboAoFechar.SelectedIndex = (int)cfg.AoFechar;
        ChkIniciarWindows.IsChecked = IntegracaoWindows.IniciaComWindows();
        ChkIniciarServidor.IsChecked = cfg.IniciarServidorAutomaticamente;
        ChkAbrirMinimizado.IsChecked = cfg.AbrirMinimizado;
        ChkPerguntarNovo.IsChecked = cfg.PerguntarAntesDeNovoDispositivo;
        ChkConfirmarEnergia.IsChecked = cfg.ConfirmarDesligarReiniciar;
        ChkEnergia.IsChecked = cfg.PermitirEnergia;
        ChkArquivos.IsChecked = cfg.PermitirArquivos;
        ChkTela.IsChecked = cfg.PermitirTelaRemota;
        ChkClipboard.IsChecked = cfg.SincronizarAreaTransferencia;
        ChkSomenteLan.IsChecked = cfg.SomenteRedeLocal;
        CampoPorta.Text = _servidor.PortaEmUso.ToString();

        AplicarEstado(new EstadoServidor(_servidor.Ativo, _servidor.Pausado, _servidor.Conectados,
            _servidor.UltimoErro ?? (_servidor.Ativo ? "Servidor ativo" : "Servidor parado")));
        SelecionarPagina(0);
        AtualizarDispositivos();
        AtualizarPin(true);
        AtualizarConexao();
        AtualizarLog();
        AjustarLayoutResponsivo();
        _relogio.Start();

        if (cfg.AbrirMinimizado && !_encerrando)
        {
            Hide();
            _bandeja.ShowBalloonTip(1500, "PCFlow", "O servidor está rodando na bandeja.", Forms.ToolTipIcon.Info);
        }
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (_encerrando) { base.OnClosing(e); return; }

        GuardarTamanhoJanela();
        var acao = _servidor.Configuracao.AoFechar;
        if (acao == AcaoAoFechar.PerguntarSempre)
        {
            var r = System.Windows.MessageBox.Show(this,
                "Deseja manter o PCFlow rodando na bandeja?\n\n" +
                "Sim — continua controlando o PC pelo celular.\n" +
                "Não — encerra o servidor.",
                "PCFlow", MessageBoxButton.YesNoCancel, MessageBoxImage.Question);
            if (r == MessageBoxResult.Cancel) { e.Cancel = true; return; }
            acao = r == MessageBoxResult.Yes ? AcaoAoFechar.MinimizarParaBandeja : AcaoAoFechar.Encerrar;
        }

        if (acao == AcaoAoFechar.MinimizarParaBandeja)
        {
            e.Cancel = true;
            Hide();
            _bandeja.ShowBalloonTip(1500, "PCFlow continua ativo",
                "O servidor segue rodando. Clique no ícone para reabrir.", Forms.ToolTipIcon.Info);
            return;
        }

        Encerrar();
        base.OnClosing(e);
    }

    private void GuardarTamanhoJanela()
    {
        if (WindowState != WindowState.Normal) return;
        _servidor.Configuracao.JanelaLargura = ActualWidth;
        _servidor.Configuracao.JanelaAltura = ActualHeight;
        _servidor.SalvarConfiguracao();
    }

    private void Encerrar()
    {
        if (_encerrando) return;
        _encerrando = true;
        _relogio.Stop();
        _bandeja.Visible = false;
        _bandeja.Dispose();
        // Limite de tempo: fechar o app nunca pode depender de o socket cooperar.
        try { _servidor.PararAsync().Wait(TimeSpan.FromSeconds(3)); } catch (Exception) { }
        System.Windows.Application.Current.Shutdown();
    }

    private Forms.ContextMenuStrip MontarMenuBandeja()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Abrir PCFlow", null, (_, _) => Restaurar());
        menu.Items.Add("Dispositivos", null, (_, _) => { Restaurar(); SelecionarPagina(1); });
        menu.Items.Add("Configurações", null, (_, _) => { Restaurar(); SelecionarPagina(4); });
        menu.Items.Add(new Forms.ToolStripSeparator());
        var pausar = new Forms.ToolStripMenuItem("Pausar servidor");
        pausar.Click += (_, _) =>
        {
            _servidor.AlternarPausa();
            pausar.Text = _servidor.Pausado ? "Retomar servidor" : "Pausar servidor";
        };
        menu.Items.Add(pausar);
        menu.Items.Add("Reiniciar servidor", null, async (_, _) => await _servidor.ReiniciarAsync());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Sair", null, (_, _) => Dispatcher.Invoke(Encerrar));
        return menu;
    }

    private void Restaurar()
    {
        Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        AjustarParaTela();
        Activate();
        Topmost = true;
        Topmost = false;
    }

    // ------------------------------------------------------------------
    // Estado e páginas
    // ------------------------------------------------------------------

    private void AplicarEstado(EstadoServidor e)
    {
        TextoStatus.Text = e.Conectados > 0
            ? $"{e.Mensagem} · {e.Conectados} dispositivo(s) conectado(s)"
            : e.Mensagem;

        var cor = !e.Ativo ? (Brush)FindResource("Alerta")
            : e.Pausado ? (Brush)FindResource("Destaque")
            : e.Conectados > 0 ? (Brush)FindResource("Sucesso")
            : (Brush)FindResource("Sucesso");
        LuzStatus.Fill = cor;
        TextoStatus.Foreground = cor;

        BotaoPausar.Content = e.Pausado ? "Retomar" : "Pausar";
        BotaoPausar.IsEnabled = e.Ativo;
        BotaoLigar.Content = e.Ativo ? "Parar servidor" : "Iniciar servidor";

        _bandeja.Text = e.Ativo
            ? (e.Conectados > 0 ? $"PCFlow — {e.Conectados} conectado(s)" : "PCFlow — aguardando")
            : "PCFlow — parado";
        AtualizarConexao();
    }

    private void Navegar_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b && int.TryParse(b.Tag?.ToString(), out var i)) SelecionarPagina(i);
    }

    private void SelecionarPagina(int indice)
    {
        _pagina = indice;
        PaginaInicio.Visibility = indice == 0 ? Visibility.Visible : Visibility.Collapsed;
        PaginaDispositivos.Visibility = indice == 1 ? Visibility.Visible : Visibility.Collapsed;
        PaginaConexao.Visibility = indice == 2 ? Visibility.Visible : Visibility.Collapsed;
        PaginaDiagnostico.Visibility = indice == 3 ? Visibility.Visible : Visibility.Collapsed;
        PaginaAjustes.Visibility = indice == 4 ? Visibility.Visible : Visibility.Collapsed;

        TituloPagina.Text = indice switch
        {
            1 => "Dispositivos",
            2 => "Conexão",
            3 => "Diagnóstico",
            4 => "Ajustes",
            _ => "Início"
        };

        var botoes = new[] { NavInicio, NavDispositivos, NavConexao, NavDiagnostico, NavAjustes };
        for (var i = 0; i < botoes.Length; i++)
        {
            botoes[i].Foreground = i == indice
                ? (Brush)FindResource("Destaque")
                : new SolidColorBrush(Color.FromRgb(0xC8, 0xCF, 0xD8));
            botoes[i].BorderBrush = i == indice
                ? (Brush)FindResource("DestaqueFraco")
                : System.Windows.Media.Brushes.Transparent;
        }

        if (indice == 2) { AtualizarConexao(); AtualizarInfoFirewall(); }
        if (indice == 3) AtualizarLog();
    }

    // ------------------------------------------------------------------
    // Início
    // ------------------------------------------------------------------

    private void AtualizarPin(bool forcarQr)
    {
        var pin = _servidor.Pin.Pin;
        var novoTexto = GerenciadorPin.Formatar(pin);
        var mudou = TextoPin.Text != novoTexto;
        TextoPin.Text = novoTexto;

        var restante = _servidor.Pin.TempoRestante;
        TextoExpira.Text = restante > TimeSpan.Zero
            ? $"Expira em {restante:mm\\:ss}"
            : "Gerando novo código…";

        EnderecoPc.Text = $"{RedeUtil.EnderecoLocal()} · porta {_servidor.PortaEmUso} · rede local";
        if (mudou || forcarQr) DesenharQr(pin);
    }

    private void DesenharQr(string pin)
    {
        try
        {
            var conteudo = $"pcflow://{RedeUtil.EnderecoLocal()}:{_servidor.PortaEmUso}" +
                           $"?pin={pin}&nome={Uri.EscapeDataString(_servidor.NomeMaquina)}&v={Protocolo.Versao}";
            ImagemQr.Source = GeradorQr.Gerar(conteudo, 168);
        }
        catch (Exception)
        {
            ImagemQr.Source = null;
        }
    }

    private void NovoPin_Click(object sender, RoutedEventArgs e)
    {
        _servidor.Pin.Renovar();
        AtualizarPin(true);
        _servidor.Log.Escrever(Categoria.Autenticacao, "Novo código de pareamento gerado");
    }

    private void CopiarEndereco_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            System.Windows.Clipboard.SetText($"{RedeUtil.EnderecoLocal()}:{_servidor.PortaEmUso}");
            System.Windows.MessageBox.Show(this,
                "Endereço copiado. No app Android use “Digitar endereço manualmente”.",
                "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception) { }
    }

    // ------------------------------------------------------------------
    // Dispositivos
    // ------------------------------------------------------------------

    private sealed record LinhaDispositivo(string Id, string Nome, string Situacao, string Quando,
        string Detalhe, string TextoBloqueio, Brush Cor);

    private void AtualizarDispositivos()
    {
        var itens = _servidor.Dispositivos.Select(d => new LinhaDispositivo(
            d.Id,
            d.Nome,
            d.Bloqueado ? "Bloqueado" : d.Conectado ? "Conectado agora" : "Autorizado",
            d.UltimaConexao.Date == DateTime.Today
                ? $"Hoje, {d.UltimaConexao:HH:mm}"
                : d.UltimaConexao.ToString("dd/MM HH:mm"),
            $"{(string.IsNullOrWhiteSpace(d.Modelo) ? "Android" : d.Modelo)} · IP {(string.IsNullOrWhiteSpace(d.UltimoIp) ? "—" : d.UltimoIp)} · pareado em {d.PareadoEm:dd/MM/yyyy}",
            d.Bloqueado ? "Desbloquear" : "Bloquear",
            d.Bloqueado ? (Brush)FindResource("Alerta")
                : d.Conectado ? (Brush)FindResource("Sucesso")
                : (Brush)FindResource("Secundario"))).ToList();

        ListaDispositivos.ItemsSource = itens;
        ListaResumo.ItemsSource = itens.Take(5).ToList();
        AvisoDispositivosVazio.Visibility = itens.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        AvisoSemDispositivos.Visibility = itens.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private static string? IdDoBotao(object remetente)
        => (remetente as Button)?.Tag?.ToString();

    private void Renomear_Click(object sender, RoutedEventArgs e)
    {
        var id = IdDoBotao(sender);
        if (id is null) return;
        var atual = _servidor.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (atual is null) return;

        var novo = DialogoTexto.Perguntar(this, "Renomear dispositivo",
            "Como você quer chamar este celular?", atual.Nome);
        if (!string.IsNullOrWhiteSpace(novo)) _servidor.RenomearDispositivo(id, novo);
    }

    private void Bloquear_Click(object sender, RoutedEventArgs e)
    {
        var id = IdDoBotao(sender);
        if (id is null) return;
        var atual = _servidor.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (atual is null) return;
        _servidor.DefinirBloqueio(id, !atual.Bloqueado);
    }

    private void Remover_Click(object sender, RoutedEventArgs e)
    {
        var id = IdDoBotao(sender);
        if (id is null) return;
        var atual = _servidor.Dispositivos.FirstOrDefault(d => d.Id == id);
        if (atual is null) return;
        var r = System.Windows.MessageBox.Show(this,
            $"Remover “{atual.Nome}”?\n\nEle precisará do PIN para conectar de novo.",
            "PCFlow", MessageBoxButton.YesNo, MessageBoxImage.Warning);
        if (r == MessageBoxResult.Yes) _servidor.RemoverDispositivo(id);
    }

    private bool PerguntarAutorizacao(string nome, string ip)
        => Dispatcher.Invoke(() =>
        {
            Restaurar();
            var r = System.Windows.MessageBox.Show(this,
                $"“{nome}” ({ip}) informou o PIN correto e quer controlar este PC.\n\nPermitir?",
                "Novo dispositivo", MessageBoxButton.YesNo, MessageBoxImage.Question);
            return r == MessageBoxResult.Yes;
        });

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

    private void AtualizarConexao()
    {
        if (!_carregado) return;
        var ips = RedeUtil.EnderecosLocais();
        InfoRede.Text = ips.Count > 0
            ? "Endereços deste PC: " + string.Join(", ", ips.Select(i => i.ToString()))
            : "Nenhuma rede ativa encontrada.";
        InfoPortas.Text = $"Controle: TCP {_servidor.PortaEmUso} · Descoberta: UDP {Protocolo.PortaDescoberta}";
        InfoDescoberta.Text = _servidor.DescobertaAtiva
            ? "Descoberta automática ativa — o celular encontra este PC sozinho."
            : "Descoberta automática indisponível. Use “Digitar endereço manualmente” no celular.";

        AtualizarInfoFirewall();

        InfoDiagnostico.Text =
            $"Servidor: {(_servidor.Ativo ? (_servidor.Pausado ? "pausado" : "ativo") : "parado")}\n" +
            $"Dispositivos conectados: {_servidor.Conectados}\n" +
            $"Pareados: {_servidor.Dispositivos.Count}\n" +
            $"Protocolo: v{Protocolo.Versao} · App v{Protocolo.VersaoApp}\n" +
            $"Endereço: {RedeUtil.EnderecoLocal()}:{_servidor.PortaEmUso}";
    }

    private bool? _firewallOk;

    /// <summary>
    /// Consultar o firewall roda "netsh", que custa centenas de milissegundos.
    /// A consulta acontece em segundo plano e só quando a página Conexão está
    /// aberta — antes, cada conexão de celular travava a interface por um instante.
    /// </summary>
    private void AtualizarInfoFirewall(bool forcar = false)
    {
        if (_firewallOk is not null && !forcar)
        {
            InfoFirewall.Text = _firewallOk == true
                ? "Regras do PCFlow encontradas no firewall."
                : "Nenhuma regra do PCFlow encontrada. Se a conexão falhar, use o botão abaixo.";
            return;
        }
        if (_pagina != 2 && !forcar) return;

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
            var r = System.Windows.MessageBox.Show(this,
                "Para criar a regra do firewall o PCFlow precisa ser aberto como administrador uma única vez.\n\n" +
                "Reabrir agora com permissão de administrador?",
                "Firewall", MessageBoxButton.YesNo, MessageBoxImage.Question);
            if (r != MessageBoxResult.Yes) return;
            if (IntegracaoWindows.ReabrirComoAdministrador("--firewall"))
            {
                Encerrar();
                return;
            }
            System.Windows.MessageBox.Show(this, "A elevação foi cancelada.", "Firewall",
                MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var ok = IntegracaoWindows.GarantirRegrasFirewall(_servidor.PortaEmUso, out var detalhe);
        _servidor.Log.Escrever(ok ? Categoria.Conexao : Categoria.Erro, detalhe);
        _firewallOk = null;
        AtualizarConexao();
        AtualizarInfoFirewall(forcar: true);
        System.Windows.MessageBox.Show(this, detalhe, "Firewall", MessageBoxButton.OK,
            ok ? MessageBoxImage.Information : MessageBoxImage.Warning);
    }

    private async void TestarPorta_Click(object sender, RoutedEventArgs e)
    {
        var porta = _servidor.PortaEmUso;
        var mensagem = await Task.Run(() =>
        {
            try
            {
                using var cliente = new System.Net.Sockets.TcpClient();
                var conectou = cliente.ConnectAsync(System.Net.IPAddress.Loopback, porta)
                    .Wait(TimeSpan.FromSeconds(3));
                return conectou
                    ? $"A porta {porta} está aceitando conexões neste PC.\n\n" +
                      "Se o celular ainda falhar, o bloqueio está no firewall ou o Wi‑Fi é outro."
                    : $"A porta {porta} não respondeu. O servidor está parado?";
            }
            catch (Exception ex) { return $"Falha no teste: {ex.Message}"; }
        });
        System.Windows.MessageBox.Show(this, mensagem, "Teste de porta",
            MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private async void Reiniciar_Click(object sender, RoutedEventArgs e)
    {
        var ok = await _servidor.ReiniciarAsync();
        if (!ok && _servidor.UltimoErro is not null)
            System.Windows.MessageBox.Show(this, _servidor.UltimoErro, "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        AtualizarConexao();
    }

    private async void AplicarPorta_Click(object sender, RoutedEventArgs e)
    {
        if (!int.TryParse(CampoPorta.Text.Trim(), out var porta) || porta < 1024 || porta > 65535)
        {
            System.Windows.MessageBox.Show(this, "Informe uma porta entre 1024 e 65535.", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
            CampoPorta.Text = _servidor.PortaEmUso.ToString();
            return;
        }
        _servidor.Configuracao.PortaControle = porta;
        _servidor.SalvarConfiguracao();
        var ok = await _servidor.ReiniciarAsync();
        CampoPorta.Text = _servidor.PortaEmUso.ToString();
        AtualizarConexao();
        AtualizarPin(true);
        if (!ok)
            System.Windows.MessageBox.Show(this, _servidor.UltimoErro ?? "Falha ao reiniciar.", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    // ------------------------------------------------------------------
    // Diagnóstico
    // ------------------------------------------------------------------

    private void AtualizarLog()
    {
        if (!_carregado || _pagina != 3) return;
        ListaLog.ItemsSource = _servidor.Log.Linhas.Reverse().Take(200).Select(l => l.ToString()).ToList();
        AtualizarConexao();
    }

    private void Exportar_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var pasta = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PCFlow");
            var arquivo = _servidor.Log.Exportar(pasta);
            System.Windows.MessageBox.Show(this, $"Diagnóstico salvo em:\n{arquivo}", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show(this, $"Não foi possível exportar: {ex.Message}", "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void LimparLog_Click(object sender, RoutedEventArgs e) => ListaLog.ItemsSource = null;

    // ------------------------------------------------------------------
    // Ajustes
    // ------------------------------------------------------------------

    private void AoFechar_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_carregado) return;
        _servidor.Configuracao.AoFechar = (AcaoAoFechar)Math.Max(0, ComboAoFechar.SelectedIndex);
        _servidor.SalvarConfiguracao();
    }

    private void Config_Click(object sender, RoutedEventArgs e)
    {
        if (!_carregado) return;
        var cfg = _servidor.Configuracao;
        cfg.IniciarServidorAutomaticamente = ChkIniciarServidor.IsChecked == true;
        cfg.AbrirMinimizado = ChkAbrirMinimizado.IsChecked == true;
        cfg.PerguntarAntesDeNovoDispositivo = ChkPerguntarNovo.IsChecked == true;
        cfg.ConfirmarDesligarReiniciar = ChkConfirmarEnergia.IsChecked == true;
        cfg.PermitirEnergia = ChkEnergia.IsChecked == true;
        cfg.PermitirArquivos = ChkArquivos.IsChecked == true;
        cfg.PermitirTelaRemota = ChkTela.IsChecked == true;
        cfg.SincronizarAreaTransferencia = ChkClipboard.IsChecked == true;
        cfg.SomenteRedeLocal = ChkSomenteLan.IsChecked == true;
        _servidor.SalvarConfiguracao();

        if (ReferenceEquals(sender, ChkIniciarWindows))
        {
            IntegracaoWindows.DefinirIniciaComWindows(ChkIniciarWindows.IsChecked == true);
            ChkIniciarWindows.IsChecked = IntegracaoWindows.IniciaComWindows();
        }
    }

    // ------------------------------------------------------------------

    private void Minimizar_Click(object sender, RoutedEventArgs e)
    {
        GuardarTamanhoJanela();
        Hide();
    }

    private void Pausar_Click(object sender, RoutedEventArgs e) => _servidor.AlternarPausa();

    private async void LigarDesligar_Click(object sender, RoutedEventArgs e)
    {
        if (_servidor.Ativo) await _servidor.PararAsync();
        else if (!_servidor.Iniciar() && _servidor.UltimoErro is not null)
            System.Windows.MessageBox.Show(this, _servidor.UltimoErro, "PCFlow",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        AtualizarConexao();
        AtualizarPin(true);
    }
}
