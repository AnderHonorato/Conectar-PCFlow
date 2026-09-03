using System.Runtime.InteropServices;
using PCFlow.Core;

namespace PCFlow.Windows.Plataforma;

/// <summary>
/// Injeção de mouse e teclado via SendInput.
///
/// Correção importante: a v1 truncava o deslocamento para int, então movimentos
/// lentos do dedo (0,4 px por evento) viravam zero e o ponteiro "não andava".
/// Agora a fração é acumulada entre eventos.
/// </summary>
public sealed class EntradaWindows : IControleEntrada
{
    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_ABSOLUTE = 0x8000;
    private const uint MOUSEEVENTF_VIRTUALDESK = 0x4000;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x1000;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;
    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;

    private const int SM_XVIRTUALSCREEN = 76;
    private const int SM_YVIRTUALSCREEN = 77;
    private const int SM_CXVIRTUALSCREEN = 78;
    private const int SM_CYVIRTUALSCREEN = 79;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx; public int dy; public uint mouseData;
        public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk; public ushort wScan; public uint dwFlags;
        public uint time; public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [DllImport("user32.dll")] private static extern int GetSystemMetrics(int nIndex);

    private readonly object _trava = new();
    private double _restoX, _restoY;

    public void MoverRelativo(double dx, double dy)
    {
        int px, py;
        lock (_trava)
        {
            _restoX += dx;
            _restoY += dy;
            px = (int)Math.Truncate(_restoX);
            py = (int)Math.Truncate(_restoY);
            _restoX -= px;
            _restoY -= py;
        }
        if (px == 0 && py == 0) return;
        Enviar(new INPUT
        {
            type = INPUT_MOUSE,
            U = new InputUnion { mi = new MOUSEINPUT { dx = px, dy = py, dwFlags = MOUSEEVENTF_MOVE } }
        });
    }

    public void MoverAbsoluto(double xNormalizado, double yNormalizado)
    {
        // Coordenadas absolutas do SendInput usam 0..65535 sobre a mesa virtual inteira.
        var x = (int)Math.Round(Math.Clamp(xNormalizado, 0, 1) * 65535);
        var y = (int)Math.Round(Math.Clamp(yNormalizado, 0, 1) * 65535);
        lock (_trava) { _restoX = 0; _restoY = 0; }
        Enviar(new INPUT
        {
            type = INPUT_MOUSE,
            U = new InputUnion
            {
                mi = new MOUSEINPUT
                {
                    dx = x,
                    dy = y,
                    dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK
                }
            }
        });
    }

    public void Botao(BotaoMouse botao, AcaoBotao acao)
    {
        var (down, up) = botao switch
        {
            BotaoMouse.Direito => (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
            BotaoMouse.Meio => (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
            _ => (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
        };
        switch (acao)
        {
            case AcaoBotao.Pressionar: Enviar(Mouse(down)); break;
            case AcaoBotao.Soltar: Enviar(Mouse(up)); break;
            default: Enviar(Mouse(down), Mouse(up)); break;
        }
    }

    public void Rolar(int dx, int dy)
    {
        var lista = new List<INPUT>(2);
        if (dy != 0)
            lista.Add(new INPUT
            {
                type = INPUT_MOUSE,
                U = new InputUnion { mi = new MOUSEINPUT { mouseData = unchecked((uint)dy), dwFlags = MOUSEEVENTF_WHEEL } }
            });
        if (dx != 0)
            lista.Add(new INPUT
            {
                type = INPUT_MOUSE,
                U = new InputUnion { mi = new MOUSEINPUT { mouseData = unchecked((uint)dx), dwFlags = MOUSEEVENTF_HWHEEL } }
            });
        if (lista.Count > 0) Enviar(lista.ToArray());
    }

    public void DigitarTexto(string texto)
    {
        // KEYEVENTF_UNICODE aceita qualquer caractere, inclusive acentos do PT-BR.
        // Pares substitutos (emoji) precisam ir como duas unidades UTF-16.
        var entradas = new List<INPUT>(texto.Length * 2);
        foreach (var c in texto)
        {
            if (c == '\n' || c == '\r')
            {
                entradas.Add(Tecla(0x0D, false));
                entradas.Add(Tecla(0x0D, true));
                continue;
            }
            entradas.Add(Unicode(c, false));
            entradas.Add(Unicode(c, true));
            if (entradas.Count >= 200) { Enviar(entradas.ToArray()); entradas.Clear(); }
        }
        if (entradas.Count > 0) Enviar(entradas.ToArray());
    }

    public void PressionarTecla(string tecla, IReadOnlyList<string>? modificadores)
    {
        var vk = Teclas.Resolver(tecla);
        if (vk is null) return;

        var mods = new List<ushort>();
        if (modificadores is not null)
            foreach (var m in modificadores)
                if (Teclas.ResolverModificador(m) is { } mv && !mods.Contains(mv))
                    mods.Add(mv);

        var entradas = new List<INPUT>(mods.Count * 2 + 2);
        foreach (var m in mods) entradas.Add(Tecla(m, false));
        entradas.Add(Tecla(vk.Value, false));
        entradas.Add(Tecla(vk.Value, true));
        for (var i = mods.Count - 1; i >= 0; i--) entradas.Add(Tecla(mods[i], true));
        Enviar(entradas.ToArray());
    }

    private static INPUT Mouse(uint flags) => new()
    {
        type = INPUT_MOUSE,
        U = new InputUnion { mi = new MOUSEINPUT { dwFlags = flags } }
    };

    private static INPUT Unicode(char c, bool soltar) => new()
    {
        type = INPUT_KEYBOARD,
        U = new InputUnion
        {
            ki = new KEYBDINPUT
            {
                wScan = c,
                dwFlags = KEYEVENTF_UNICODE | (soltar ? KEYEVENTF_KEYUP : 0)
            }
        }
    };

    private static INPUT Tecla(ushort vk, bool soltar)
    {
        // Teclas de navegação e Windows são "estendidas": sem a flag, Ctrl+Seta falha em alguns apps.
        var estendida = vk is 0x21 or 0x22 or 0x23 or 0x24 or 0x25 or 0x26 or 0x27 or 0x28
            or 0x2D or 0x2E or 0x5B or 0x5C or 0x5D or 0xA6 or 0xA7 or 0xA8 or 0xAA or 0xAC
            or 0xAD or 0xAE or 0xAF or 0xB0 or 0xB1 or 0xB2 or 0xB3;
        return new INPUT
        {
            type = INPUT_KEYBOARD,
            U = new InputUnion
            {
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    dwFlags = (soltar ? KEYEVENTF_KEYUP : 0) | (estendida ? KEYEVENTF_EXTENDEDKEY : 0)
                }
            }
        };
    }

    private static void Enviar(params INPUT[] entradas)
    {
        if (entradas.Length == 0) return;
        SendInput((uint)entradas.Length, entradas, Marshal.SizeOf<INPUT>());
    }

    public static (int X, int Y, int Largura, int Altura) MesaVirtual() => (
        GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN),
        GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN));
}

public sealed class MidiaWindows : IControleMidia
{
    private readonly EntradaWindows _entrada;
    public MidiaWindows(EntradaWindows entrada) => _entrada = entrada;

    public void Executar(string acao)
    {
        var nome = acao.ToLowerInvariant() switch
        {
            "playpause" or "play" or "pause" => "PLAYPAUSE",
            "next" or "proxima" => "NEXTTRACK",
            "previous" or "anterior" => "PREVTRACK",
            "stop" or "parar" => "STOPMEDIA",
            "volumeup" or "volumemais" => "VOLUMEUP",
            "volumedown" or "volumemenos" => "VOLUMEDOWN",
            "mute" or "mudo" => "VOLUMEMUTE",
            _ => ""
        };
        if (nome.Length > 0) _entrada.PressionarTecla(nome, null);
    }
}
