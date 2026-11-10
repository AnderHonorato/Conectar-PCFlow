using PCFlow.Core;

namespace PCFlow.Windows.Core;

public sealed class EntradaDoAgente : ICommandSink
{
    public void Execute(RemoteCommand command) => ExecutorComandos.Aplicar(command);
    public void ReleaseHeld(string kind, string code) => ExecutorComandos.Soltar(kind, code);
}
