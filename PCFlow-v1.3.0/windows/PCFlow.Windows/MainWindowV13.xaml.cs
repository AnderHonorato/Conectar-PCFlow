using Microsoft.Win32;
using PCFlow.Windows.Core;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net.NetworkInformation;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using Forms = System.Windows.Forms;
using WpfButton = System.Windows.Controls.Button;
using WpfMessageBox = System.Windows.MessageBox;

namespace PCFlow.Windows;

public partial class MainWindowV13 : Window
{
    private readonly ServidorPcFlow _servidor = new();
    private readonly ServidorArquivosPcFlow _servidorArquivos = new();
    private readonly Forms.NotifyIcon _tray;
    private readonly ObservableCollection<string> _atividade = new();
    private readonly DispatcherTimer _previewTimer;
    private MolduraSessaoWindow? _moldura;
    private bool _encerrando;
    private bool _carregandoUi = true;
    private DispositivoLinhaV13? _dispositivoSelecionado;
    private string _caminhoLocal;
    private string _caminhoCompartilhado;

    public MainWindowV13()
    {
        InitializeComponent();

        _caminhoLocal = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        _caminhoCompartilhado = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "PCFlow", "Compartilhado");
        Directory.CreateDirectory(_caminhoCompartilhado);

        _tray = new Forms.NotifyIcon
        {
            Text = "PCFlow V1.3 — acesso remoto",
            Visible = true,
            Icon = System.Drawing.SystemIcons.Application,
            ContextMenuStrip = CriarMenuTray()
        };
        _tray.DoubleClick += (_, _) => Restaurar();

        ListaAtividade.ItemsSource = _atividade;
        ListaArquivosLocal.MouseDoubleClick += (_, _) => AbrirItemArquivo(ListaArquivosLocal, true);
        ListaArquivosCompartilhados.MouseDoubleClick += (_, _) => AbrirItemArquivo(ListaArquivosCompartilhados, false);
        CampoSenha.PasswordChanged += (_, _) => AtualizarForcaSenha();

        _servidor.StatusAlterado += status => Dispatcher.Invoke(() => RegistrarStatus(status));
        _servidorArquivos.Status += status => Dispatcher.Invoke(() => RegistrarStatus(status));
        _servidor.DispositivosAlterados += () => Dispatcher.Invoke(AtualizarTudo);
        _servidor.SessoesAlteradas += quantidade => Dispatcher.Invoke(() => AtualizarMoldura(quantidade));
        _servidor.SolicitarAceiteAsync = SolicitarAceiteAsync;
        _servidor.JanelaVisivel = () => Dispatcher.Invoke(() => IsVisible && WindowState != WindowState.Minimized);
        ExecutorComandos.ChatRecebido += MensagemChatRecebida;

        _previewTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(900) };
        _previewTimer.Tick += (_, _) => AtualizarPreview();

        Loaded += async (_, _) =>
        {
            await _servidor.IniciarAsync();
            await _servidorArquivos.IniciarAsync();
            CarregarConfiguracaoNaTela();
            PopularMonitores();
            AtualizarTudo();
            NavegarPara("inicio");
            _previewTimer.Start();
            _carregandoUi = false;
        };
    }

    private Forms.ContextMenuStrip CriarMenuTray()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Abrir PCFlow", null, (_, _) => Restaurar());
        menu.Items.Add("Copiar meu ID", null, (_, _) => Dispatcher.Invoke(() => CopiarId()));
        menu.Items.Add("Pausar/retomar servidor", null, (_, _) => Dispatcher.Invoke(() => AlternarPausa()));
        menu.Items.Add("Sair", null, async (_, _) => await EncerrarAsync());
        return menu;
    }

    private Task<bool> SolicitarAceiteAsync(SolicitacaoConexao solicitacao)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            Restaurar();
            if (_servidor.Configuracao.NotificarConexoes)
                _tray.ShowBalloonTip(1800, "Solicitação de acesso", $"{solicitacao.Nome} quer controlar este computador.", Forms.ToolTipIcon.Info);

            _atividade.Insert(0, $"{DateTime.Now:HH:mm} · Solicitação de {solicitacao.Nome} ({solicitacao.EnderecoIp})");
            LimitarAtividade();
            var conhecido = solicitacao.DispositivoConhecido ? "Dispositivo já conhecido." : "Novo dispositivo.";
            var resposta = WpfMessageBox.Show(this,
                $"{solicitacao.Nome} ({solicitacao.EnderecoIp}) quer iniciar uma sessão remota.\n\n{conhecido}\n\nPermitir acesso?",
                "PCFlow — Solicitação de conexão", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No) == MessageBoxResult.Yes;
            _atividade.Insert(0, $"{DateTime.Now:HH:mm} · Conexão {(resposta ? "aceita" : "negada")} · {solicitacao.Nome}");
            LimitarAtividade();
            return resposta;
        }).Task;
    }

    private void MensagemChatRecebida(string texto)
    {
        Dispatcher.Invoke(() =>
        {
            _atividade.Insert(0, $"{DateTime.Now:HH:mm} · Mensagem remota: {texto}");
            LimitarAtividade();
            _tray.ShowBalloonTip(2500, "PCFlow — mensagem", texto, Forms.ToolTipIcon.Info);
            TextoStatus.Text = "Mensagem recebida";
        });
    }

    private void RegistrarStatus(string status)
    {
        TextoStatus.Text = status;
        StatusBolinha.Fill = status.Contains("paus", StringComparison.OrdinalIgnoreCase) || status.Contains("erro", StringComparison.OrdinalIgnoreCase)
            ? Brushes.OrangeRed : (Brush)FindResource("Sucesso");
        _atividade.Insert(0, $"{DateTime.Now:HH:mm} · {status}");
        LimitarAtividade();
        if (_servidor.Configuracao.NotificarConexoes && (status.Contains("conectado", StringComparison.OrdinalIgnoreCase) || status.Contains("recus", StringComparison.OrdinalIgnoreCase)))
            _tray.ShowBalloonTip(1600, "PCFlow", status, Forms.ToolTipIcon.Info);
        AtualizarTudoLeve();
    }

    private void LimitarAtividade()
    {
        while (_atividade.Count > 40) _atividade.RemoveAt(_atividade.Count - 1);
    }

    private void AtualizarTudo()
    {
        AtualizarTudoLeve();
        AtualizarDispositivos();
        AtualizarArquivos();
        AtualizarPreview();
    }

    private void AtualizarTudoLeve()
    {
        TextoMaquinaId.Text = FormatarId(_servidor.MaquinaId);
        TextoCodigoRemoto.Text = FormatarId(_servidor.MaquinaId);
        TextoPin.Text = FormatarPin(_servidor.CodigoPareamento);
        TextoEndereco.Text = $"{Environment.MachineName} · {_servidor.EnderecoLocal}:{ServidorPcFlow.PortaControle}";
        TextoNomePc.Text = Environment.MachineName;
        TextoSistema.Text = Environment.OSVersion.VersionString;
        TextoHardware.Text = $"{Environment.ProcessorCount} núcleos lógicos · {Math.Round(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / 1024d / 1024d / 1024d, 1)} GB memória disponível";

        var payload = $"pcflow://connect?host={Uri.EscapeDataString(_servidor.EnderecoLocal)}&port={ServidorPcFlow.PortaControle}&id={_servidor.MaquinaId}&pin={_servidor.CodigoPareamento}&tls={_servidor.ImpressaoTls}&nome={Uri.EscapeDataString(Environment.MachineName)}";
        ImagemQr.Source = QrCodeVisual.Criar(payload);

        QuickPermitirConexoes.IsChecked = _servidor.Configuracao.AcessoInterativo != "nunca";
        QuickExigirConfirmacao.IsChecked = _servidor.Configuracao.AcessoInterativo == "sempre";
        QuickSenhaAtiva.IsChecked = !string.IsNullOrWhiteSpace(_servidor.Configuracao.SenhaHash);

        var recentes = _servidor.Dispositivos.OrderByDescending(x => x.UltimaConexao).Take(6)
            .Select(d => new ItemResumoV13($"{d.Nome} · {(d.Bloqueado ? "Bloqueado" : "Autorizado")} · {d.UltimaConexao.ToLocalTime():dd/MM HH:mm}"))
            .ToList();
        ListaRecentesInicio.ItemsSource = recentes;
        ListaConfiaveis.ItemsSource = _servidor.Dispositivos.Where(d => !d.Bloqueado).OrderByDescending(d => d.UltimaConexao)
            .Select(d => new ItemResumoV13($"{d.Nome} · {d.UltimaConexao.ToLocalTime():dd/MM HH:mm}"))
            .ToList();
    }

    private void AtualizarDispositivos()
    {
        var busca = BuscaDispositivos.Text?.Trim() ?? "";
        var statusIndex = FiltroStatus.SelectedIndex;
        var somenteFavoritos = FiltroFavoritos.SelectedIndex == 1;
        var linhas = _servidor.Dispositivos
            .Where(d => string.IsNullOrWhiteSpace(busca) || d.Nome.Contains(busca, StringComparison.CurrentCultureIgnoreCase) || d.Id.Contains(busca, StringComparison.OrdinalIgnoreCase))
            .Where(d => statusIndex switch { 1 => !d.Bloqueado, 2 => d.Bloqueado, _ => true })
            .Where(d => !somenteFavoritos || d.Favorito)
            .OrderByDescending(d => d.Favorito)
            .ThenByDescending(d => d.UltimaConexao)
            .Select(d => new DispositivoLinhaV13(d))
            .ToList();
        ListaDispositivos.ItemsSource = linhas;
        if (_dispositivoSelecionado is not null)
        {
            var atual = linhas.FirstOrDefault(x => x.Id == _dispositivoSelecionado.Id);
            if (atual is not null) SelecionarDispositivo(atual);
            else LimparSelecaoDispositivo();
        }
    }

    private void CarregarConfiguracaoNaTela()
    {
        var c = _servidor.Configuracao;
        AcessoSempre.IsChecked = c.AcessoInterativo == "sempre";
        AcessoJanela.IsChecked = c.AcessoInterativo == "janela";
        AcessoNunca.IsChecked = c.AcessoInterativo == "nunca";
        CheckTela.IsChecked = c.PermitirTela;
        CheckEntrada.IsChecked = c.PermitirEntrada;
        CheckClipboard.IsChecked = c.PermitirClipboard;
        CheckEnergia.IsChecked = c.PermitirEnergia;
        CheckArquivos.IsChecked = c.PermitirArquivos;
        CheckDescoberta.IsChecked = c.DescobertaRede;
        CheckMoldura.IsChecked = c.MolduraSessao;

        RemotoEntrada.IsChecked = c.PermitirEntrada;
        RemotoClipboard.IsChecked = c.PermitirClipboard;
        RemotoArquivos.IsChecked = c.PermitirArquivos;
        RemotoEnergia.IsChecked = c.PermitirEnergia;

        ConfigIniciarWindows.IsChecked = c.IniciarComWindows;
        ConfigMinimizar.IsChecked = c.MinimizarParaBandeja;
        ConfigDescoberta.IsChecked = c.DescobertaRede;
        ConfigMostrarOffline.IsChecked = c.MostrarDispositivosOffline;
        ConfigSons.IsChecked = c.SonsInterface;
        ConfigAnonimos.IsChecked = c.ColetarUsoAnonimo;
        ConfigNotifConexoes.IsChecked = c.NotificarConexoes;
        ConfigNotifTransferencias.IsChecked = c.NotificarTransferencias;
        ConfigNotifNovos.IsChecked = c.NotificarNovosDispositivos;
        ConfigIdioma.SelectedIndex = 0;

        ComboQualidade.SelectedIndex = c.QualidadePadrao switch { "alta" => 1, "equilibrada" => 2, "economica" => 3, _ => 0 };
        ComboFps.SelectedIndex = c.FpsPadrao switch { 15 => 0, 30 => 1, _ => 2 };
        ComboModoToque.SelectedIndex = c.ModoToquePadrao == "touchpad" ? 1 : 0;
        AplicarTema(c.Tema);
        AplicarDestaque(c.CorDestaque);
    }

    private void SalvarSeguranca_Click(object sender, RoutedEventArgs e)
    {
        SalvarSeguranca();
        WpfMessageBox.Show(this, "Configurações de segurança salvas.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void SalvarSeguranca()
    {
        var c = _servidor.Configuracao;
        c.AcessoInterativo = AcessoNunca.IsChecked == true ? "nunca" : AcessoJanela.IsChecked == true ? "janela" : "sempre";
        c.PermitirTela = CheckTela.IsChecked == true;
        c.PermitirEntrada = CheckEntrada.IsChecked == true;
        c.PermitirClipboard = CheckClipboard.IsChecked == true;
        c.PermitirEnergia = CheckEnergia.IsChecked == true;
        c.PermitirArquivos = CheckArquivos.IsChecked == true;
        c.DescobertaRede = CheckDescoberta.IsChecked == true;
        c.MolduraSessao = CheckMoldura.IsChecked == true;
        _servidor.SalvarConfiguracao();
        AtualizarTudoLeve();
    }

    private void Seguranca_Changed(object sender, RoutedEventArgs e)
    {
        if (_carregandoUi) return;
        SalvarSeguranca();
    }

    private void QuickSeguranca_Changed(object sender, RoutedEventArgs e)
    {
        if (_carregandoUi) return;
        var c = _servidor.Configuracao;
        if (QuickPermitirConexoes.IsChecked != true) c.AcessoInterativo = "nunca";
        else c.AcessoInterativo = QuickExigirConfirmacao.IsChecked == true ? "sempre" : "janela";
        _servidor.SalvarConfiguracao();
        CarregarConfiguracaoNaTela();
    }

    private void DefinirSenha_Click(object sender, RoutedEventArgs e)
    {
        if (!_servidor.DefinirSenhaNaoSupervisionada(CampoSenha.Password))
        {
            WpfMessageBox.Show(this, "Use uma senha com pelo menos 8 caracteres.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        CampoSenha.Clear();
        AtualizarForcaSenha();
        AtualizarTudoLeve();
        RegistrarStatus("Senha de acesso não assistido alterada");
    }

    private void AplicarSenhaRemoto_Click(object sender, RoutedEventArgs e)
    {
        if (!_servidor.DefinirSenhaNaoSupervisionada(CampoSenhaRemoto.Password))
        {
            WpfMessageBox.Show(this, "A senha precisa ter pelo menos 8 caracteres.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        CampoSenhaRemoto.Clear();
        AtualizarTudoLeve();
        RegistrarStatus("Senha adicional aplicada");
    }

    private void RemoverSenha_Click(object sender, RoutedEventArgs e)
    {
        _servidor.RemoverSenhaNaoSupervisionada();
        CampoSenha.Clear();
        AtualizarTudoLeve();
        RegistrarStatus("Senha de acesso não assistido removida");
    }

    private void AtualizarForcaSenha()
    {
        var s = CampoSenha.Password;
        var pontos = 0;
        if (s.Length >= 8) pontos++;
        if (s.Any(char.IsUpper) && s.Any(char.IsLower)) pontos++;
        if (s.Any(char.IsDigit)) pontos++;
        if (s.Any(ch => !char.IsLetterOrDigit(ch))) pontos++;
        ForcaSenha.Value = pontos;
    }

    private void FiltroDispositivos_Changed(object sender, EventArgs e)
    {
        if (_carregandoUi || ListaDispositivos is null) return;
        AtualizarDispositivos();
    }

    private void ListaDispositivos_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ListaDispositivos.SelectedItem is DispositivoLinhaV13 linha) SelecionarDispositivo(linha);
    }

    private void SelecionarDispositivo(DispositivoLinhaV13 linha)
    {
        _dispositivoSelecionado = linha;
        DetalheNome.Text = linha.Nome;
        DetalheId.Text = $"ID: {linha.IdCurto}";
        DetalheUltima.Text = $"Última conexão: {linha.Ultima}";
    }

    private void LimparSelecaoDispositivo()
    {
        _dispositivoSelecionado = null;
        DetalheNome.Text = "Selecione um dispositivo";
        DetalheId.Text = "";
        DetalheUltima.Text = "";
    }

    private DispositivoAutorizado? ObterSelecionado() => _dispositivoSelecionado is null ? null : _servidor.Configuracao.Dispositivos.FirstOrDefault(x => x.Id == _dispositivoSelecionado.Id);

    private void FavoritarDispositivo_Click(object sender, RoutedEventArgs e)
    {
        var d = ObterSelecionado();
        if (d is null) return;
        d.Favorito = !d.Favorito;
        _servidor.SalvarConfiguracao();
        AtualizarDispositivos();
    }

    private void RenomearDispositivo_Click(object sender, RoutedEventArgs e)
    {
        var d = ObterSelecionado();
        if (d is null) return;
        var novo = Microsoft.VisualBasic.Interaction.InputBox("Novo nome para o dispositivo:", "PCFlow — Renomear", d.Nome);
        if (string.IsNullOrWhiteSpace(novo)) return;
        d.Nome = novo.Trim();
        _servidor.SalvarConfiguracao();
        AtualizarDispositivos();
        AtualizarTudoLeve();
    }

    private void AlternarBloqueio_Click(object sender, RoutedEventArgs e)
    {
        var d = ObterSelecionado();
        if (d is null) return;
        d.Bloqueado = !d.Bloqueado;
        _servidor.SalvarConfiguracao();
        RegistrarStatus(d.Bloqueado ? $"{d.Nome} bloqueado" : $"{d.Nome} liberado");
        AtualizarDispositivos();
    }

    private void RevogarDispositivo_Click(object sender, RoutedEventArgs e)
    {
        var d = ObterSelecionado();
        if (d is null) return;
        if (WpfMessageBox.Show(this, $"Revogar o acesso salvo de {d.Nome}?", "PCFlow", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) != MessageBoxResult.Yes) return;
        _servidor.Configuracao.Dispositivos.Remove(d);
        _servidor.SalvarConfiguracao();
        LimparSelecaoDispositivo();
        AtualizarDispositivos();
        AtualizarTudoLeve();
    }

    private void AdicionarDispositivo_Click(object sender, RoutedEventArgs e)
    {
        _servidor.GerarNovoCodigo();
        AtualizarTudoLeve();
        NavegarPara("inicio");
        System.Windows.Clipboard.SetText($"ID: {FormatarId(_servidor.MaquinaId)} · Código: {FormatarPin(_servidor.CodigoPareamento)}");
        WpfMessageBox.Show(this, "Um novo código de pareamento foi gerado e copiado. Escaneie o QR ou use ID + código no novo dispositivo.", "Adicionar dispositivo", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void PopularMonitores()
    {
        ComboMonitor.Items.Clear();
        for (var i = 0; i < Math.Max(1, CapturaTela.QuantidadeMonitores); i++)
            ComboMonitor.Items.Add(new ComboBoxItem { Content = $"Monitor {i + 1}{(i == 0 ? " (Principal)" : "")}", Tag = i });
        ComboMonitor.SelectedIndex = 0;
    }

    private void ConfiguracaoRemota_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_carregandoUi) return;
        var c = _servidor.Configuracao;
        c.QualidadePadrao = (ComboQualidade.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "automatica";
        c.FpsPadrao = int.TryParse((ComboFps.SelectedItem as ComboBoxItem)?.Content?.ToString(), out var fps) ? fps : 60;
        c.ModoToquePadrao = (ComboModoToque.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "touch";
        _servidor.SalvarConfiguracao();
        AtualizarPreview();
    }

    private void AtualizarPreview()
    {
        if (!IsLoaded || PaginaRemoto.Visibility != Visibility.Visible) return;
        try
        {
            var monitor = (ComboMonitor.SelectedItem as ComboBoxItem)?.Tag is int i ? i : 0;
            var qualidade = _servidor.Configuracao.QualidadePadrao switch { "alta" => 82, "equilibrada" => 68, "economica" => 48, _ => 72 };
            var bytes = CapturaTela.CapturarJpeg(monitor, qualidade);
            if (bytes.Length == 0) return;
            using var ms = new MemoryStream(bytes);
            var imagem = new BitmapImage();
            imagem.BeginInit();
            imagem.CacheOption = BitmapCacheOption.OnLoad;
            imagem.StreamSource = ms;
            imagem.EndInit();
            imagem.Freeze();
            PreviewTela.Source = imagem;
            PreviewInfo.Text = $"Monitor {monitor + 1} · {imagem.PixelWidth} × {imagem.PixelHeight} · {_servidor.Configuracao.FpsPadrao} FPS alvo";
        }
        catch (Exception ex)
        {
            PreviewInfo.Text = $"Prévia indisponível: {ex.Message}";
        }
    }

    private void IniciarTeste_Click(object sender, RoutedEventArgs e)
    {
        var monitor = (ComboMonitor.SelectedItem as ComboBoxItem)?.Tag is int i ? i : 0;
        new JanelaTesteRemotoV13(monitor, _servidor.Configuracao.FpsPadrao, _servidor.Configuracao.QualidadePadrao).Show();
    }

    private void AtualizarArquivos()
    {
        Directory.CreateDirectory(_caminhoLocal);
        Directory.CreateDirectory(_caminhoCompartilhado);
        CaminhoLocal.Text = _caminhoLocal;
        CaminhoCompartilhado.Text = _caminhoCompartilhado;
        ListaArquivosLocal.ItemsSource = ListarPasta(_caminhoLocal);
        ListaArquivosCompartilhados.ItemsSource = ListarPasta(_caminhoCompartilhado);
    }

    private static List<ArquivoPainelV13> ListarPasta(string caminho)
    {
        var lista = new List<ArquivoPainelV13>();
        var dir = new DirectoryInfo(caminho);
        if (dir.Parent is not null) lista.Add(ArquivoPainelV13.Pai(dir.Parent.FullName));
        try
        {
            lista.AddRange(dir.EnumerateDirectories().Where(x => !x.Attributes.HasFlag(FileAttributes.System)).OrderBy(x => x.Name).Select(ArquivoPainelV13.DeDiretorio));
            lista.AddRange(dir.EnumerateFiles().Where(x => !x.Attributes.HasFlag(FileAttributes.System)).OrderBy(x => x.Name).Select(ArquivoPainelV13.DeArquivo));
        }
        catch { }
        return lista.Take(1000).ToList();
    }

    private void AbrirItemArquivo(ListBox lista, bool local)
    {
        if (lista.SelectedItem is not ArquivoPainelV13 item || !item.Pasta) return;
        if (local) _caminhoLocal = item.Caminho; else _caminhoCompartilhado = item.Caminho;
        AtualizarArquivos();
    }

    private async void EnviarArquivo_Click(object sender, RoutedEventArgs e)
    {
        if (ListaArquivosLocal.SelectedItem is not ArquivoPainelV13 item || item.EhPai) return;
        await CopiarItemAsync(item, _caminhoCompartilhado, "Enviando para a área compartilhada…");
    }

    private async void BaixarArquivo_Click(object sender, RoutedEventArgs e)
    {
        if (ListaArquivosCompartilhados.SelectedItem is not ArquivoPainelV13 item || item.EhPai) return;
        var downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "PCFlow", "Baixados");
        Directory.CreateDirectory(downloads);
        await CopiarItemAsync(item, downloads, "Baixando da área compartilhada…");
    }

    private async Task CopiarItemAsync(ArquivoPainelV13 item, string destinoPasta, string mensagem)
    {
        try
        {
            ProgressoTransferencia.IsIndeterminate = true;
            TextoTransferencia.Text = mensagem;
            await Task.Run(() =>
            {
                var destino = Path.Combine(destinoPasta, item.Nome);
                if (item.Pasta) CopiarDiretorio(item.Caminho, destino);
                else File.Copy(item.Caminho, destino, true);
            });
            ProgressoTransferencia.IsIndeterminate = false;
            ProgressoTransferencia.Value = 100;
            TextoTransferencia.Text = $"Concluído: {item.Nome}";
            if (_servidor.Configuracao.NotificarTransferencias)
                _tray.ShowBalloonTip(1600, "PCFlow — transferência concluída", item.Nome, Forms.ToolTipIcon.Info);
            AtualizarArquivos();
        }
        catch (Exception ex)
        {
            ProgressoTransferencia.IsIndeterminate = false;
            ProgressoTransferencia.Value = 0;
            TextoTransferencia.Text = $"Falha: {ex.Message}";
        }
    }

    private static void CopiarDiretorio(string origem, string destino)
    {
        Directory.CreateDirectory(destino);
        foreach (var arquivo in Directory.EnumerateFiles(origem)) File.Copy(arquivo, Path.Combine(destino, Path.GetFileName(arquivo)), true);
        foreach (var pasta in Directory.EnumerateDirectories(origem)) CopiarDiretorio(pasta, Path.Combine(destino, Path.GetFileName(pasta)));
    }

    private void AtualizarArquivos_Click(object sender, RoutedEventArgs e) => AtualizarArquivos();

    private void AbrirCompartilhada_Click(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(_caminhoCompartilhado);
        Process.Start(new ProcessStartInfo("explorer.exe", _caminhoCompartilhado) { UseShellExecute = true });
    }

    private void SalvarConfiguracoes_Click(object sender, RoutedEventArgs e)
    {
        var c = _servidor.Configuracao;
        c.IniciarComWindows = ConfigIniciarWindows.IsChecked == true;
        c.MinimizarParaBandeja = ConfigMinimizar.IsChecked == true;
        c.DescobertaRede = ConfigDescoberta.IsChecked == true;
        c.MostrarDispositivosOffline = ConfigMostrarOffline.IsChecked == true;
        c.SonsInterface = ConfigSons.IsChecked == true;
        c.ColetarUsoAnonimo = ConfigAnonimos.IsChecked == true;
        c.NotificarConexoes = ConfigNotifConexoes.IsChecked == true;
        c.NotificarTransferencias = ConfigNotifTransferencias.IsChecked == true;
        c.NotificarNovosDispositivos = ConfigNotifNovos.IsChecked == true;
        c.Idioma = (ConfigIdioma.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "pt-BR";
        _servidor.SalvarConfiguracao();
        ConfigurarInicializacaoWindows(c.IniciarComWindows);
        WpfMessageBox.Show(this, "Configurações salvas.", "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private static void ConfigurarInicializacaoWindows(bool ativar)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true);
            if (ativar)
            {
                var exe = Environment.ProcessPath;
                if (!string.IsNullOrWhiteSpace(exe)) key?.SetValue("PCFlow", $"\"{exe}\"");
            }
            else key?.DeleteValue("PCFlow", false);
        }
        catch { }
    }

    private void Tema_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not WpfButton b) return;
        var tema = b.Tag?.ToString() ?? "escuro";
        _servidor.Configuracao.Tema = tema;
        _servidor.SalvarConfiguracao();
        AplicarTema(tema);
    }

    private void AplicarTema(string tema)
    {
        var claro = tema == "claro" || (tema == "automatico" && SystemParameters.HighContrast);
        Resources["Fundo"] = new SolidColorBrush(claro ? Color.FromRgb(240, 243, 247) : Color.FromRgb(8, 11, 15));
        Resources["Sidebar"] = new SolidColorBrush(claro ? Color.FromRgb(232, 236, 241) : Color.FromRgb(13, 18, 24));
        Resources["Painel"] = new SolidColorBrush(claro ? Colors.White : Color.FromRgb(20, 27, 34));
        Resources["Painel2"] = new SolidColorBrush(claro ? Color.FromRgb(231, 236, 242) : Color.FromRgb(27, 36, 45));
        Resources["Borda"] = new SolidColorBrush(claro ? Color.FromRgb(194, 203, 213) : Color.FromRgb(48, 58, 69));
        Resources["Texto"] = new SolidColorBrush(claro ? Color.FromRgb(23, 29, 36) : Color.FromRgb(244, 247, 250));
        Resources["Texto2"] = new SolidColorBrush(claro ? Color.FromRgb(91, 102, 114) : Color.FromRgb(158, 171, 184));
    }

    private void CorDestaque_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not WpfButton b) return;
        var cor = b.Tag?.ToString() ?? "dourado";
        _servidor.Configuracao.CorDestaque = cor;
        _servidor.SalvarConfiguracao();
        AplicarDestaque(cor);
    }

    private void AplicarDestaque(string nome)
    {
        var cor = nome switch
        {
            "azul" => Color.FromRgb(47, 128, 237),
            "turquesa" => Color.FromRgb(36, 210, 194),
            "roxo" => Color.FromRgb(139, 92, 246),
            "rosa" => Color.FromRgb(236, 72, 153),
            _ => Color.FromRgb(243, 177, 63)
        };
        Resources["Destaque"] = new SolidColorBrush(cor);
        MarcarMenuSelecionado(_paginaAtual);
    }

    private void VerificarAtualizacoes_Click(object sender, RoutedEventArgs e)
    {
        Process.Start(new ProcessStartInfo("https://github.com/AnderHonorato/Conectar-PCFlow/releases") { UseShellExecute = true });
    }

    private string _paginaAtual = "inicio";

    private void Navegar_Click(object sender, RoutedEventArgs e)
    {
        if (sender is WpfButton b) NavegarPara(b.Tag?.ToString() ?? "inicio");
    }

    private void NavegarPara(string pagina)
    {
        _paginaAtual = pagina;
        PaginaInicio.Visibility = pagina == "inicio" ? Visibility.Visible : Visibility.Collapsed;
        PaginaDispositivos.Visibility = pagina == "dispositivos" ? Visibility.Visible : Visibility.Collapsed;
        PaginaSeguranca.Visibility = pagina == "seguranca" ? Visibility.Visible : Visibility.Collapsed;
        PaginaRemoto.Visibility = pagina == "remoto" ? Visibility.Visible : Visibility.Collapsed;
        PaginaTransferencia.Visibility = pagina == "transferencia" ? Visibility.Visible : Visibility.Collapsed;
        PaginaConfiguracoes.Visibility = pagina == "configuracoes" ? Visibility.Visible : Visibility.Collapsed;

        var (titulo, subtitulo) = pagina switch
        {
            "dispositivos" => ("Dispositivos", "Gerencie dispositivos autorizados"),
            "seguranca" => ("Segurança", "Permissões, confiança e acesso"),
            "remoto" => ("Acesso Remoto", "Qualidade, entrada e prévia"),
            "transferencia" => ("Transferência", "Arquivos locais e área compartilhada"),
            "configuracoes" => ("Configurações", "Personalize o PCFlow"),
            _ => ("Início", "Central de acesso remoto")
        };
        TituloTopo.Text = titulo;
        SubtituloTopo.Text = subtitulo;
        MarcarMenuSelecionado(pagina);
        if (pagina == "dispositivos") AtualizarDispositivos();
        if (pagina == "transferencia") AtualizarArquivos();
        if (pagina == "remoto") AtualizarPreview();
    }

    private void MarcarMenuSelecionado(string pagina)
    {
        var destaque = FindResource("Destaque") as Brush ?? Brushes.Goldenrod;
        foreach (var b in new[] { MenuInicio, MenuDispositivos, MenuSeguranca, MenuRemoto, MenuTransferencia, MenuConfiguracoes })
        {
            var ativo = (b.Tag?.ToString() ?? "") == pagina;
            b.Foreground = ativo ? destaque : (FindResource("Texto") as Brush ?? Brushes.White);
            b.Background = ativo ? new SolidColorBrush(Color.FromArgb(35, 243, 177, 63)) : Brushes.Transparent;
            b.BorderBrush = ativo ? destaque : Brushes.Transparent;
        }
    }

    private void CopiarId_Click(object sender, RoutedEventArgs e) => CopiarId();
    private void CopiarId() { System.Windows.Clipboard.SetText(_servidor.MaquinaId); TextoStatus.Text = "ID copiado"; }
    private void CopiarPin_Click(object sender, RoutedEventArgs e) { System.Windows.Clipboard.SetText(_servidor.CodigoPareamento); TextoStatus.Text = "Código copiado"; }
    private void NovoCodigo_Click(object sender, RoutedEventArgs e) { _servidor.GerarNovoCodigo(); AtualizarTudoLeve(); TextoStatus.Text = "Novo código gerado"; }

    private void Pausar_Click(object sender, RoutedEventArgs e) => AlternarPausa();
    private void AlternarPausa()
    {
        _servidor.AlternarPausa();
        BotaoPausar.Content = _servidor.Pausado ? "▶  Retomar" : "Ⅱ  Pausar";
        RegistrarStatus(_servidor.Pausado ? "Servidor pausado" : "Servidor ativo");
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
            ExecutorComandos.ChatRecebido -= MensagemChatRecebida;
            _previewTimer.Stop();
            _tray.Visible = false;
            _tray.Dispose();
            _servidorArquivos.DisposeAsync().AsTask().GetAwaiter().GetResult();
            _servidor.DisposeAsync().AsTask().GetAwaiter().GetResult();
            System.Windows.Application.Current.Shutdown();
        }
        base.OnClosing(e);
    }

    private void Minimizar_Click(object sender, RoutedEventArgs e) => Hide();
    private async void Encerrar_Click(object sender, RoutedEventArgs e) => await EncerrarAsync();
    private void Restaurar() { Show(); WindowState = WindowState.Normal; Activate(); }

    private async Task EncerrarAsync()
    {
        if (_encerrando) return;
        _encerrando = true;
        ExecutorComandos.ChatRecebido -= MensagemChatRecebida;
        _previewTimer.Stop();
        _moldura?.Close();
        _tray.Visible = false;
        _tray.Dispose();
        await _servidorArquivos.DisposeAsync();
        await _servidor.DisposeAsync();
        Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
    }

    private sealed record ItemResumoV13(string Resumo);

    private sealed class DispositivoLinhaV13
    {
        public string Id { get; }
        public string Nome { get; }
        public string IdCurto { get; }
        public string Ultima { get; }
        public string Status { get; }
        public string FavoritoTexto { get; }
        public DispositivoLinhaV13(DispositivoAutorizado d)
        {
            Id = d.Id;
            Nome = d.Nome;
            IdCurto = d.Id.Length > 18 ? d.Id[..18] + "…" : d.Id;
            Ultima = d.UltimaConexao.ToLocalTime().ToString("dd/MM/yyyy HH:mm");
            Status = d.Bloqueado ? "Bloqueado" : "Confiável";
            FavoritoTexto = d.Favorito ? "★" : "☆";
        }
    }

    private sealed class ArquivoPainelV13
    {
        public string Nome { get; init; } = "";
        public string Caminho { get; init; } = "";
        public bool Pasta { get; init; }
        public bool EhPai { get; init; }
        public long Tamanho { get; init; }
        public DateTime Modificado { get; init; }
        public string Resumo => EhPai ? "↰  .." : Pasta ? $"📁  {Nome}" : $"{Nome}   ·   {FormatarTamanho(Tamanho)}   ·   {Modificado:dd/MM HH:mm}";
        public static ArquivoPainelV13 Pai(string caminho) => new() { Nome = "..", Caminho = caminho, Pasta = true, EhPai = true };
        public static ArquivoPainelV13 DeDiretorio(DirectoryInfo d) => new() { Nome = d.Name, Caminho = d.FullName, Pasta = true, Modificado = d.LastWriteTime };
        public static ArquivoPainelV13 DeArquivo(FileInfo f) => new() { Nome = f.Name, Caminho = f.FullName, Pasta = false, Tamanho = f.Length, Modificado = f.LastWriteTime };
        private static string FormatarTamanho(long n) => n >= 1024L * 1024L * 1024L ? $"{n / 1024d / 1024d / 1024d:0.0} GB" : n >= 1024L * 1024L ? $"{n / 1024d / 1024d:0.0} MB" : n >= 1024 ? $"{n / 1024d:0.0} KB" : $"{n} B";
    }
}
