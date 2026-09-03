using System.Diagnostics;
using System.Runtime.InteropServices;

namespace PCFlow.Windows.Core;

public static class ExecutorComandos
{
    [DllImport("user32.dll")] private static extern bool LockWorkStation();
    [DllImport("user32.dll", SetLastError = true)] private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    private static readonly IntPtr HWND_BROADCAST = new(0xffff);
    private const uint WM_SYSCOMMAND = 0x0112;
    private static readonly IntPtr SC_MONITORPOWER = new(0xF170);

    public static void Executar(MensagemRede m)
    {
        switch (m.Tipo)
        {
            case "mouse_move": EntradaWindows.Mover(m.X, m.Y); break;
            case "mouse_abs": EntradaWindows.MoverAbsoluto(m.X, m.Y, m.Monitor); break;
            case "mouse_click": EntradaWindows.Clique(m.Botao ?? "left"); break;
            case "mouse_down": EntradaWindows.BotaoBaixar(m.Botao ?? "left"); break;
            case "mouse_up": EntradaWindows.BotaoSoltar(m.Botao ?? "left"); break;
            case "scroll": EntradaWindows.Scroll(m.Delta); break;
            case "texto": if (!string.IsNullOrEmpty(m.Texto)) EntradaWindows.Texto(m.Texto); break;
            case "tecla": ExecutarTecla(m.Tecla); break;
            case "media": ExecutarMedia(m.Acao); break;
            case "power": ExecutarEnergia(m.Acao); break;
        }
    }

    private static void ExecutarTecla(string? tecla)
    {
        var vk = tecla?.ToUpperInvariant() switch
        {
            "ENTER" => (ushort)0x0D, "ESC" => (ushort)0x1B, "TAB" => (ushort)0x09,
            "BACKSPACE" => (ushort)0x08, "DELETE" => (ushort)0x2E, "SPACE" => (ushort)0x20,
            "LEFT" => (ushort)0x25, "UP" => (ushort)0x26, "RIGHT" => (ushort)0x27, "DOWN" => (ushort)0x28,
            "HOME" => (ushort)0x24, "END" => (ushort)0x23, "PAGEUP" => (ushort)0x21, "PAGEDOWN" => (ushort)0x22,
            "F1" => (ushort)0x70, "F2" => (ushort)0x71, "F3" => (ushort)0x72, "F4" => (ushort)0x73,
            "F5" => (ushort)0x74, "F6" => (ushort)0x75, "F7" => (ushort)0x76, "F8" => (ushort)0x77,
            "F9" => (ushort)0x78, "F10" => (ushort)0x79, "F11" => (ushort)0x7A, "F12" => (ushort)0x7B,
            _ => (ushort)0
        };
        if (vk != 0) EntradaWindows.Tecla(vk);
    }

    private static void ExecutarMedia(string? acao)
    {
        var vk = acao?.ToLowerInvariant() switch
        {
            "playpause" => (ushort)0xB3, "next" => (ushort)0xB0, "previous" => (ushort)0xB1,
            "stop" => (ushort)0xB2, "volumeup" => (ushort)0xAF, "volumedown" => (ushort)0xAE, "mute" => (ushort)0xAD,
            _ => (ushort)0
        };
        if (vk != 0) EntradaWindows.Tecla(vk);
    }

    private static void ExecutarEnergia(string? acao)
    {
        switch (acao?.ToLowerInvariant())
        {
            case "lock": LockWorkStation(); break;
            case "monitoroff": SendMessage(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, new IntPtr(2)); break;
            case "sleep": Process.Start(new ProcessStartInfo("rundll32.exe", "powrprof.dll,SetSuspendState 0,1,0") { CreateNoWindow = true }); break;
            case "hibernate": Process.Start(new ProcessStartInfo("shutdown", "/h") { CreateNoWindow = true }); break;
            case "shutdown": Process.Start(new ProcessStartInfo("shutdown", "/s /t 0") { CreateNoWindow = true }); break;
            case "restart": Process.Start(new ProcessStartInfo("shutdown", "/r /t 0") { CreateNoWindow = true }); break;
            case "signout": Process.Start(new ProcessStartInfo("shutdown", "/l") { CreateNoWindow = true }); break;
        }
    }
}
