using System.Threading;
using System.Windows;

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
                "O PCFlow já está em execução. Ele pode estar minimizado na bandeja do Windows.\n\nAbra o ícone do PCFlow perto do relógio ou escolha Sair na versão antiga antes de iniciar uma nova versão.",
                "PCFlow já está aberto",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            Shutdown();
            return;
        }

        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        new MainWindow().Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { _mutex?.ReleaseMutex(); } catch { }
        _mutex?.Dispose();
        base.OnExit(e);
    }
}
