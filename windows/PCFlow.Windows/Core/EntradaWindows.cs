using System.Runtime.InteropServices;

namespace PCFlow.Windows.Core;

public static class EntradaWindows
{
    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    public static void Mover(double x, double y)
    {
        var input = new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dx = (int)x, dy = (int)y, dwFlags = MOUSEEVENTF_MOVE } } };
        Enviar(input);
    }

    public static void Clique(string botao)
    {
        var (down, up) = botao.ToLowerInvariant() switch
        {
            "right" or "direito" => (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
            "middle" or "meio" => (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
            _ => (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
        };
        Enviar(Mouse(down), Mouse(up));
    }

    public static void Scroll(int delta)
        => Enviar(new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { mouseData = unchecked((uint)delta), dwFlags = MOUSEEVENTF_WHEEL } } });

    public static void Texto(string texto)
    {
        foreach (var c in texto)
        {
            Enviar(
                new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = c, dwFlags = KEYEVENTF_UNICODE } } },
                new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = c, dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP } } });
        }
    }

    public static void Tecla(ushort vk)
    {
        Enviar(
            new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = vk } } },
            new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = vk, dwFlags = KEYEVENTF_KEYUP } } });
    }

    private static INPUT Mouse(uint flags) => new() { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = flags } } };
    private static void Enviar(params INPUT[] inputs) => SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
}
