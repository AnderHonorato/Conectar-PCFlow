using System.IO;
using System.Threading;
using System.Windows;
using System.Windows.Threading;
using PCFlow.Windows.Core;

namespace PCFlow.Windows;

public partial class App : System.Windows.Application
{
    private Mutex? _mutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        const string nomeMutex = "Local\\PCFlow.Windows.Singleton.v1";
        _mutex = new Mutex(true, nomeMutex, out var primeiraInstancia);
        if (!primeiraInstancia)
        {
            System.Windows.MessageBox.Show(
                "O PCFlow já está em execução. Ele pode estar minimizado na bandeja do Windows.\n\n" +
                "Abra o ícone do PCFlow perto do relógio, ou escolha Sair na cópia antiga antes de iniciar outra.",
                "PCFlow já está aberto",
                MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        // Nada pode fechar o app em silêncio: tudo vira relatório em Documentos\PCFlow.
        DispatcherUnhandledException += (_, args) =>
        {
            Registrar(args.Exception);
            args.Handled = true;
            System.Windows.MessageBox.Show(
                "O PCFlow encontrou um erro, mas continua funcionando.\n\n" +
                $"Detalhe: {args.Exception.Message}\n\nO relatório foi salvo em Documentos\\PCFlow.",
                "PCFlow", MessageBoxButton.OK, MessageBoxImage.Warning);
        };
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
        {
            if (args.ExceptionObject is Exception ex) Registrar(ex);
        };
        TaskScheduler.UnobservedTaskException += (_, args) => { args.SetObserved(); Registrar(args.Exception); };

        // Reaberto com elevação só para gravar a regra do firewall.
        if (e.Args.Contains("--firewall"))
            IntegracaoWindows.GarantirRegrasFirewall(out _);

        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        new MainWindow().Show();
    }

    private static void Registrar(Exception ex)
    {
        try
        {
            var pasta = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PCFlow");
            Directory.CreateDirectory(pasta);
            File.WriteAllText(
                Path.Combine(pasta, $"erro-{DateTime.Now:yyyyMMdd-HHmmss}.txt"),
                $"PCFlow {VersaoPcFlow.App}\n{DateTime.Now:dd/MM/yyyy HH:mm:ss}\n\n{ex}");
        }
        catch (Exception) { /* falhar ao registrar não pode derrubar o app */ }
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { _mutex?.ReleaseMutex(); } catch (Exception) { }
        _mutex?.Dispose();
        base.OnExit(e);
    }
}
