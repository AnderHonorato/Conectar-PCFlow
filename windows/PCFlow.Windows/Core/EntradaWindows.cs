using System.Diagnostics;
using System.Runtime.InteropServices;
using Forms = System.Windows.Forms;

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
    private const uint MOUSEEVENTF_HWHEEL = 0x1000;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;

    /// <summary>
    /// Unidades UTF-16 por chamada de SendInput. O lote inteiro é copiado para
    /// memória não gerenciada de uma vez, então lote gigante custa caro e ainda
    /// segura a fila de entrada do usuário enquanto é processado.
    /// </summary>
    private const int UnidadesPorLote = 128;

    [StructLayout(LayoutKind.Sequential)] private struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] private struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] private struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }

    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [DllImport("user32.dll")] private static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] private static extern uint GetDoubleClickTime();

    public static void Mover(double x, double y)
        => Enviar(new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dx = (int)x, dy = (int)y, dwFlags = MOUSEEVENTF_MOVE } } });

    public static void MoverAbsoluto(double xNormalizado, double yNormalizado, int monitor)
    {
        var telas = Forms.Screen.AllScreens;
        if (telas.Length == 0) return;
        monitor = Math.Clamp(monitor, 0, telas.Length - 1);
        var b = telas[monitor].Bounds;
        var x = b.Left + (int)(Math.Clamp(xNormalizado, 0, 1) * Math.Max(1, b.Width - 1));
        var y = b.Top + (int)(Math.Clamp(yNormalizado, 0, 1) * Math.Max(1, b.Height - 1));
        SetCursorPos(x, y);
    }

    /// <summary>
    /// Um clique, ou vários seguidos que o Windows lê como duplo/triplo.
    /// </summary>
    public static void Clique(BotaoMouse botao, int cliques = 1)
    {
        var (down, up) = FlagsBotao(botao);
        cliques = Math.Clamp(cliques, 1, InstrucaoEntrada.MaximoCliques);

        var inputs = new INPUT[cliques * 2];
        for (var i = 0; i < cliques; i++)
        {
            inputs[i * 2] = Mouse(down);
            inputs[i * 2 + 1] = Mouse(up);
        }

        // Os pares vão num lote só: assim chegam com o mesmo carimbo de tempo e
        // o Windows enxerga um duplo clique de verdade (abrir pasta, selecionar
        // palavra). Se o lote for cortado no meio — outra thread injetando
        // entrada bloqueia SendInput — só vale completar enquanto ainda estamos
        // dentro do intervalo de duplo clique do sistema; depois dele o resto
        // viraria um segundo clique solto, que é pior que um clique simples.
        var enviados = (int)Enviar(inputs);
        if (enviados >= inputs.Length) return;

        var relogio = Stopwatch.StartNew();
        var limite = GetDoubleClickTime();
        while (enviados > 0 && enviados < inputs.Length && relogio.ElapsedMilliseconds < limite)
        {
            var agora = (int)Enviar(inputs[enviados..]);
            if (agora == 0) break;
            enviados += agora;
        }
    }

    public static void BotaoBaixar(BotaoMouse botao) { var (down, _) = FlagsBotao(botao); Enviar(Mouse(down)); }
    public static void BotaoSoltar(BotaoMouse botao) { var (_, up) = FlagsBotao(botao); Enviar(Mouse(up)); }

    /// <summary>
    /// Gira a roda do mouse. Convenção do PCFlow, igual à do Windows:
    /// no eixo vertical delta positivo empurra a roda para longe do usuário
    /// (a página sobe); no horizontal delta positivo é a roda inclinada para a
    /// direita, revelando o conteúdo à direita. Quem envia é que decide o sinal
    /// — aqui ele passa direto, sem inversão.
    /// </summary>
    public static void Scroll(int delta, EixoRolagem eixo = EixoRolagem.Vertical)
        => Enviar(new INPUT
        {
            type = INPUT_MOUSE,
            U = new InputUnion
            {
                mi = new MOUSEINPUT
                {
                    mouseData = unchecked((uint)delta),
                    dwFlags = eixo == EixoRolagem.Horizontal ? MOUSEEVENTF_HWHEEL : MOUSEEVENTF_WHEEL
                }
            }
        });

    public static void Texto(string texto)
    {
        if (string.IsNullOrEmpty(texto)) return;

        var inicio = 0;
        var lote = new List<INPUT>(Math.Min(texto.Length, UnidadesPorLote) * 2);
        while (inicio < texto.Length)
        {
            var fim = Math.Min(texto.Length, inicio + UnidadesPorLote);

            // Emoji e o resto de fora do BMP chegam como par substituto: as duas
            // unidades UTF-16 precisam sair no mesmo lote e coladas, senão o
            // aplicativo recebe dois caracteres inválidos em vez do símbolo.
            if (fim < texto.Length && char.IsHighSurrogate(texto[fim - 1])) fim++;

            lote.Clear();
            for (var i = inicio; i < fim; i++)
            {
                lote.Add(UnicodeInput(texto[i], false));
                lote.Add(UnicodeInput(texto[i], true));
            }
            Enviar(lote.ToArray());
            inicio = fim;
        }
    }

    public static void Tecla(ushort vk)
        => Enviar(TeclaInput(vk, false), TeclaInput(vk, true));

    public static void TeclaBaixar(ushort vk) => Enviar(TeclaInput(vk, false));
    public static void TeclaSoltar(ushort vk) => Enviar(TeclaInput(vk, true));

    /// <summary>
    /// Aplica uma sequência de pressionar/soltar num lote só, para que nenhum
    /// outro evento se intrometa entre o Ctrl e o C.
    /// </summary>
    public static void AcionarTeclas(IReadOnlyList<(ushort Tecla, bool Soltar)> passos)
    {
        if (passos.Count == 0) return;
        var inputs = new INPUT[passos.Count];
        for (var i = 0; i < passos.Count; i++) inputs[i] = TeclaInput(passos[i].Tecla, passos[i].Soltar);
        Enviar(inputs);
    }

    private static INPUT UnicodeInput(char unidade, bool soltar)
        => new() { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = unidade, dwFlags = soltar ? KEYEVENTF_UNICODE | KEYEVENTF_KEYUP : KEYEVENTF_UNICODE } } };

    private static INPUT TeclaInput(ushort vk, bool soltar)
        => new() { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = vk, dwFlags = soltar ? KEYEVENTF_KEYUP : 0 } } };

    private static (uint down, uint up) FlagsBotao(BotaoMouse botao) => botao switch
    {
        BotaoMouse.Direito => (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
        BotaoMouse.Meio => (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
        _ => (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP)
    };

    private static INPUT Mouse(uint flags) => new() { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = flags } } };
    private static uint Enviar(params INPUT[] inputs) => SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
}
