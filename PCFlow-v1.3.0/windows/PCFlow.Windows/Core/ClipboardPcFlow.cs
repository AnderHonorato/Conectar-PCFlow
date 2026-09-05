namespace PCFlow.Windows.Core;

public static class ClipboardPcFlow
{
    public static string LerTexto()
    {
        try
        {
            return System.Windows.Application.Current.Dispatcher.Invoke(() =>
                System.Windows.Clipboard.ContainsText() ? System.Windows.Clipboard.GetText() : "");
        }
        catch { return ""; }
    }

    public static void DefinirTexto(string texto)
    {
        try
        {
            System.Windows.Application.Current.Dispatcher.Invoke(() => System.Windows.Clipboard.SetText(texto ?? ""));
        }
        catch { }
    }
}
