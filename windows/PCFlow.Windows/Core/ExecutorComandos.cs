using System.Diagnostics;
using System.Runtime.InteropServices;
using PCFlow.Core;

namespace PCFlow.Windows.Core;

public static class ExecutorComandos
{
    [DllImport("user32.dll")] private static extern bool LockWorkStation();
    [DllImport("user32.dll", SetLastError = true)] private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    private static readonly IntPtr HWND_BROADCAST = new(0xffff);
    private const uint WM_SYSCOMMAND = 0x0112;
    private static readonly IntPtr SC_MONITORPOWER = new(0xF170);

    public static void Aplicar(RemoteCommand c)
    {
        switch (c.Name)
        {
            case "pointer.move": EntradaWindows.Mover(c.X, c.Y); break;
            case "pointer.button":
                if (c.Direction is "down" or "up") EntradaWindows.Botao(c.Button ?? "left", c.Direction == "down");
                else EntradaWindows.Clique(c.Button ?? "left");
                break;
            case "pointer.scroll": EntradaWindows.Scroll(c.Delta); break;
            case "keyboard.text": if (!string.IsNullOrEmpty(c.Text)) EntradaWindows.Texto(c.Text); break;
            case "keyboard.key":
                var vk = VirtualKey(c.Key);
                if (vk == 0) break;
                if (c.Direction is "down" or "up") EntradaWindows.TeclaDirecao(vk, c.Direction == "down");
                else EntradaWindows.Tecla(vk);
                break;
            case "media.transport":
            case "media.volume": ExecutarMedia(c.Action); break;
            case "system.power": ExecutarEnergia(c.Action); break;
        }
    }

    public static void Soltar(string kind, string code)
    {
        if (kind == "pointer") EntradaWindows.Botao(code, false);
        else if (kind == "key")
        {
            var vk = VirtualKey(code);
            if (vk != 0) EntradaWindows.TeclaDirecao(vk, false);
        }
    }

    public static void Executar(MensagemRede m)
    {
        switch (m.Tipo)
        {
            case "mouse_move": EntradaWindows.Mover(m.X, m.Y); break;
            case "mouse_click": EntradaWindows.Clique(m.Botao ?? "left"); break;
            case "scroll": EntradaWindows.Scroll(m.Delta); break;
            case "texto": if (!string.IsNullOrEmpty(m.Texto)) EntradaWindows.Texto(m.Texto); break;
            case "tecla": ExecutarTecla(m.Tecla); break;
            case "media": ExecutarMedia(m.Acao); break;
            case "power": ExecutarEnergia(m.Acao); break;
        }
    }

    private static void ExecutarTecla(string? tecla)
    {
        var vk = VirtualKey(tecla);
        if (vk != 0) EntradaWindows.Tecla(vk);
    }

    private static ushort VirtualKey(string? tecla) => tecla?.ToUpperInvariant() switch
    {
        "ENTER" => 0x0D, "ESC" => 0x1B, "TAB" => 0x09,
        "BACKSPACE" => 0x08, "DELETE" => 0x2E,
        "LEFT" => 0x25, "UP" => 0x26, "RIGHT" => 0x27, "DOWN" => 0x28,
        "HOME" => 0x24, "END" => 0x23, "PAGEUP" => 0x21, "PAGEDOWN" => 0x22,
        "CTRL" => 0x11, "SHIFT" => 0x10, "ALT" => 0x12, "WIN" => 0x5B,
        _ => 0
    };

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
