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

    private const ushort VK_CONTROL = 0x11;
    private const ushort VK_SHIFT = 0x10;
    private const ushort VK_MENU = 0x12;
    private const ushort VK_LWIN = 0x5B;

    public static event Action<string>? ChatRecebido;

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
            case "chat": if (!string.IsNullOrWhiteSpace(m.Texto)) ChatRecebido?.Invoke(m.Texto.Trim()); break;
        }
    }

    private static void ExecutarTecla(string? tecla)
    {
        var t = tecla?.Trim().ToUpperInvariant();
        if (string.IsNullOrEmpty(t)) return;

        if (t.Length == 1)
        {
            var c = t[0];
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))
            {
                EntradaWindows.Tecla(c);
                return;
            }
        }

        switch (t)
        {
            case "NEW_TAB": EntradaWindows.Combo(VK_CONTROL, (ushort)'T'); return;
            case "CLOSE_TAB": EntradaWindows.Combo(VK_CONTROL, (ushort)'W'); return;
            case "REOPEN_TAB": EntradaWindows.Combo(VK_CONTROL, VK_SHIFT, (ushort)'T'); return;
            case "BROWSER_BACK": EntradaWindows.Tecla(0xA6); return;
            case "BROWSER_FORWARD": EntradaWindows.Tecla(0xA7); return;
            case "BROWSER_REFRESH": EntradaWindows.Tecla(0xA8); return;
            case "BROWSER_HOME": EntradaWindows.Tecla(0xAC); return;
            case "ALT_TAB": EntradaWindows.Combo(VK_MENU, 0x09); return;
            case "SHOW_DESKTOP": EntradaWindows.Combo(VK_LWIN, (ushort)'D'); return;
            case "TASK_MANAGER": EntradaWindows.Combo(VK_CONTROL, VK_SHIFT, 0x1B); return;
            case "START_MENU": EntradaWindows.Tecla(VK_LWIN); return;
            case "WIN_E": EntradaWindows.Combo(VK_LWIN, (ushort)'E'); return;
            case "WIN_R": EntradaWindows.Combo(VK_LWIN, (ushort)'R'); return;
            case "PPT_START": EntradaWindows.Tecla(0x74); return;
            case "PPT_END": EntradaWindows.Tecla(0x1B); return;
            case "PPT_NEXT": EntradaWindows.Tecla(0x22); return;
            case "PPT_PREVIOUS": EntradaWindows.Tecla(0x21); return;
        }

        var vk = t switch
        {
            "ENTER" => (ushort)0x0D, "ESC" => (ushort)0x1B, "TAB" => (ushort)0x09,
            "BACKSPACE" => (ushort)0x08, "DELETE" => (ushort)0x2E, "SPACE" => (ushort)0x20,
            "LEFT" => (ushort)0x25, "UP" => (ushort)0x26, "RIGHT" => (ushort)0x27, "DOWN" => (ushort)0x28,
            "HOME" => (ushort)0x24, "END" => (ushort)0x23, "PAGEUP" => (ushort)0x21, "PAGEDOWN" => (ushort)0x22,
            "INSERT" => (ushort)0x2D,
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
            "playpause" => (ushort)0xB3,
            "next" => (ushort)0xB0,
            "previous" => (ushort)0xB1,
            "stop" => (ushort)0xB2,
            "volumeup" => (ushort)0xAF,
            "volumedown" => (ushort)0xAE,
            "mute" => (ushort)0xAD,
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
