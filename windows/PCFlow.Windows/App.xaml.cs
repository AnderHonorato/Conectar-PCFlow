using System.IO;
using System.Threading;
using System.Windows;
using System.Windows.Threading;
using PCFlow.Core;
using PCFlow.Windows.Plataforma;

namespace PCFlow.Windows;

public partial class App : System.Windows.Application
{
    private Mutex? _instanciaUnica;
    private ServidorPcFlow? _servidor;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        // Uma segunda cópia não conseguiria abrir as portas e morria com exceção
        // não tratada. Agora a segunda instância avisa e sai limpo.
        _instanciaUnica = new Mutex(true, @"Global\PCFlow_InstanciaUnica", out var primeira);
        if (!primeira)
        {
            System.Windows.MessageBox.Show(
                "O PCFlow já está aberto.\n\nProcure o ícone dele na bandeja, ao lado do relógio.",
                "PCFlow", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        // Nada pode fechar o app em silêncio: tudo vira log + aviso amigável.
        DispatcherUnhandledException += AoFalharNaInterface;
        AppDomain.CurrentDomain.UnhandledException += AoFalharNoProcesso;
        TaskScheduler.UnobservedTaskException += (_, args) => { args.SetObserved(); Registrar(args.Exception); };

        var entrada = new EntradaWindows();
        var plataforma = new ServicosPlataforma
        {
            Entrada = entrada,
            Midia = new MidiaWindows(entrada),
            Energia = new EnergiaWindows(),
            AreaTransferencia = new AreaTransferenciaWindows(),
            CapturaTela = new CapturaTelaWindows(),
            Lancador = new LancadorWindows(),
            NomeMaquina = Environment.MachineName
        };

        _servidor = new ServidorPcFlow(plataforma);

        // Aberto com --firewall após a elevação: cria as regras e segue normalmente.
        if (e.Args.Contains("--firewall"))
        {
            var ok = IntegracaoWindows.GarantirRegrasFirewall(_servidor.PortaEmUso, out var detalhe);
            _servidor.Log.Escrever(ok ? Categoria.Conexao : Categoria.Erro, detalhe);
        }

        if (_servidor.Configuracao.IniciarServidorAutomaticamente && !_servidor.Iniciar())
            _servidor.Log.Escrever(Categoria.Erro, _servidor.UltimoErro ?? "Falha ao iniciar o servidor.");

        if (e.Args.Contains("--minimizado")) _servidor.Configuracao.AbrirMinimizado = true;

        var janela = new MainWindow(_servidor);
        MainWindow = janela;
        janela.Show();
    }

    private void AoFalharNaInterface(object? remetente, DispatcherUnhandledExceptionEventArgs e)
    {
        Registrar(e.Exception);
        e.Handled = true;
        System.Windows.MessageBox.Show(
            "O PCFlow encontrou um erro, mas continua funcionando.\n\n" +
            $"Detalhe: {e.Exception.Message}\n\n" +
            "O relatório foi salvo em Documentos\\PCFlow.",
            "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    private void AoFalharNoProcesso(object? remetente, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex) Registrar(ex);
    }

    private static void Registrar(Exception ex)
    {
        try
        {
            var pasta = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PCFlow");
            Directory.CreateDirectory(pasta);
            var arquivo = Path.Combine(pasta, $"erro-{DateTime.Now:yyyyMMdd-HHmmss}.txt");
            File.WriteAllText(arquivo,
                $"PCFlow {Protocolo.VersaoApp}\n{DateTime.Now:dd/MM/yyyy HH:mm:ss}\n\n{ex}");
        }
        catch (Exception) { /* falhar ao registrar não pode derrubar o app */ }
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { _servidor?.PararAsync().GetAwaiter().GetResult(); } catch (Exception) { }
        _instanciaUnica?.Dispose();
        base.OnExit(e);
    }
}
