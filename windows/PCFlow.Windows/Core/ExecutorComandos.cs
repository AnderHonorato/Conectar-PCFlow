#if WINDOWS
using System.Diagnostics;
using System.Runtime.InteropServices;
#endif

namespace PCFlow.Windows.Core;

// Este arquivo tem duas metades. A de cima traduz a mensagem que chegou da rede
// em uma intenção de entrada e não depende de nada do Windows — é ela que o
// projeto de testes compila em net8.0 para rodar em Linux. A de baixo, atrás de
// #if WINDOWS, é a que de fato mexe no mouse e no teclado da máquina.

public enum AcaoEntrada
{
    Nenhuma,
    MoverRelativo,
    MoverAbsoluto,
    Clicar,
    PressionarBotao,
    SoltarBotao,
    Rolar,
    Digitar,
    Teclar
}

public enum BotaoMouse { Esquerdo, Direito, Meio }

public enum EixoRolagem { Vertical, Horizontal }

/// <summary>
/// O que fazer com o mouse ou o teclado, já resolvido a partir da mensagem de
/// rede: sem strings soltas, sem nome de tecla e sem apelido de botão.
/// </summary>
public sealed record InstrucaoEntrada
{
    /// <summary>Clique triplo ainda é útil (seleciona parágrafo); acima disso é engano.</summary>
    public const int MaximoCliques = 3;

    public static readonly InstrucaoEntrada Nenhuma = new();

    public AcaoEntrada Acao { get; init; } = AcaoEntrada.Nenhuma;
    public BotaoMouse Botao { get; init; } = BotaoMouse.Esquerdo;
    public int Cliques { get; init; } = 1;
    public double X { get; init; }
    public double Y { get; init; }
    public int Monitor { get; init; }
    public int Delta { get; init; }
    public EixoRolagem Eixo { get; init; } = EixoRolagem.Vertical;
    public string Texto { get; init; } = "";
    public IReadOnlyList<ushort> Modificadores { get; init; } = [];
    public ushort Tecla { get; init; }

    /// <summary>
    /// Ordem exata de acionamento: os modificadores na ordem recebida, a tecla,
    /// e tudo solto na ordem inversa. É essa ordem que faz Ctrl+C ser Ctrl+C e
    /// não um C seguido de um Ctrl perdido.
    /// </summary>
    public IReadOnlyList<(ushort Tecla, bool Soltar)> SequenciaDeTeclas()
    {
        if (Tecla == 0) return [];

        var passos = new List<(ushort, bool)>((Modificadores.Count + 1) * 2);
        foreach (var vk in Modificadores) passos.Add((vk, false));
        passos.Add((Tecla, false));
        passos.Add((Tecla, true));
        for (var i = Modificadores.Count - 1; i >= 0; i--) passos.Add((Modificadores[i], true));
        return passos;
    }
}

/// <summary>Converte <see cref="MensagemRede"/> em <see cref="InstrucaoEntrada"/>.</summary>
public static class TradutorEntrada
{
    public const ushort VkShift = 0x10;
    public const ushort VkControl = 0x11;
    public const ushort VkAlt = 0x12;
    public const ushort VkWin = 0x5B;

    private static readonly Dictionary<string, (ushort[] Modificadores, ushort Tecla)> Atalhos = new()
    {
        ["NEW_TAB"] = ([VkControl], 'T'),
        ["CLOSE_TAB"] = ([VkControl], 'W'),
        ["REOPEN_TAB"] = ([VkControl, VkShift], 'T'),
        ["BROWSER_BACK"] = ([], 0xA6),
        ["BROWSER_FORWARD"] = ([], 0xA7),
        ["BROWSER_REFRESH"] = ([], 0xA8),
        ["BROWSER_HOME"] = ([], 0xAC),
        ["ALT_TAB"] = ([VkAlt], 0x09),
        ["SHOW_DESKTOP"] = ([VkWin], 'D'),
        ["TASK_MANAGER"] = ([VkControl, VkShift], 0x1B),
        ["START_MENU"] = ([], VkWin),
        ["WIN_E"] = ([VkWin], 'E'),
        ["WIN_R"] = ([VkWin], 'R'),
        ["PPT_START"] = ([], 0x74),
        ["PPT_END"] = ([], 0x1B),
        ["PPT_NEXT"] = ([], 0x22),
        ["PPT_PREVIOUS"] = ([], 0x21)
    };

    public static InstrucaoEntrada Traduzir(MensagemRede m) => m.Tipo switch
    {
        "mouse_move" => new InstrucaoEntrada { Acao = AcaoEntrada.MoverRelativo, X = m.X, Y = m.Y },
        "mouse_abs" => new InstrucaoEntrada { Acao = AcaoEntrada.MoverAbsoluto, X = m.X, Y = m.Y, Monitor = m.Monitor },
        "mouse_click" => new InstrucaoEntrada { Acao = AcaoEntrada.Clicar, Botao = LerBotao(m.Botao), Cliques = LerCliques(m.Cliques) },
        "mouse_down" => new InstrucaoEntrada { Acao = AcaoEntrada.PressionarBotao, Botao = LerBotao(m.Botao) },
        "mouse_up" => new InstrucaoEntrada { Acao = AcaoEntrada.SoltarBotao, Botao = LerBotao(m.Botao) },
        "scroll" => new InstrucaoEntrada { Acao = AcaoEntrada.Rolar, Delta = m.Delta, Eixo = LerEixo(m.Eixo) },
        "texto" => string.IsNullOrEmpty(m.Texto)
            ? InstrucaoEntrada.Nenhuma
            : new InstrucaoEntrada { Acao = AcaoEntrada.Digitar, Texto = m.Texto },
        "tecla" => TraduzirTecla(m.Tecla, m.Modificadores),
        "media" => TraduzirMedia(m.Acao),
        _ => InstrucaoEntrada.Nenhuma
    };

    public static BotaoMouse LerBotao(string? botao) => botao?.Trim().ToLowerInvariant() switch
    {
        "right" or "direito" => BotaoMouse.Direito,
        "middle" or "meio" => BotaoMouse.Meio,
        _ => BotaoMouse.Esquerdo
    };

    public static EixoRolagem LerEixo(string? eixo) => eixo?.Trim().ToLowerInvariant() switch
    {
        "horizontal" or "h" => EixoRolagem.Horizontal,
        _ => EixoRolagem.Vertical
    };

    private static int LerCliques(int cliques) => cliques < 1 ? 1 : Math.Min(cliques, InstrucaoEntrada.MaximoCliques);

    /// <summary>
    /// Nomes aceitos para cada modificador. Vem tudo do celular, então vale
    /// aceitar o apelido em português além do nome do protocolo.
    /// </summary>
    private static ushort ModificadorDe(string nome) => nome.Trim().ToLowerInvariant() switch
    {
        "ctrl" or "control" or "controle" => VkControl,
        "alt" => VkAlt,
        "shift" => VkShift,
        "win" or "windows" or "meta" or "super" => VkWin,
        _ => 0
    };

    private static List<ushort> LerModificadores(IEnumerable<string>? nomes)
    {
        var teclas = new List<ushort>(4);
        if (nomes is null) return teclas;
        foreach (var nome in nomes)
        {
            if (string.IsNullOrWhiteSpace(nome)) continue;
            var vk = ModificadorDe(nome);
            if (vk != 0 && !teclas.Contains(vk)) teclas.Add(vk);
        }
        return teclas;
    }

    private static InstrucaoEntrada TraduzirTecla(string? tecla, IEnumerable<string>? modificadores)
    {
        var nome = tecla?.Trim().ToUpperInvariant();
        if (string.IsNullOrEmpty(nome)) return InstrucaoEntrada.Nenhuma;

        var mods = LerModificadores(modificadores);

        if (nome.Length == 1)
        {
            var c = nome[0];
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return Acionar(mods, c);
        }

        // Os atalhos nomeados já trazem os próprios modificadores; os que
        // vieram na mensagem entram antes, para que "ctrl" + ALT_TAB vire
        // Ctrl+Alt+Tab e não duas combinações concorrentes.
        if (Atalhos.TryGetValue(nome, out var atalho))
        {
            foreach (var vk in atalho.Modificadores)
                if (!mods.Contains(vk)) mods.Add(vk);
            return Acionar(mods, atalho.Tecla);
        }

        var codigo = TeclaNomeada(nome);
        return codigo == 0 ? InstrucaoEntrada.Nenhuma : Acionar(mods, codigo);
    }

    private static InstrucaoEntrada TraduzirMedia(string? acao)
    {
        var vk = acao?.Trim().ToLowerInvariant() switch
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
        return vk == 0 ? InstrucaoEntrada.Nenhuma : Acionar([], vk);
    }

    private static InstrucaoEntrada Acionar(IReadOnlyList<ushort> modificadores, ushort tecla)
        => new() { Acao = AcaoEntrada.Teclar, Modificadores = modificadores, Tecla = tecla };

    private static ushort TeclaNomeada(string nome) => nome switch
    {
        "ENTER" => 0x0D, "ESC" => 0x1B, "TAB" => 0x09,
        "BACKSPACE" => 0x08, "DELETE" => 0x2E, "SPACE" => 0x20,
        "LEFT" => 0x25, "UP" => 0x26, "RIGHT" => 0x27, "DOWN" => 0x28,
        "HOME" => 0x24, "END" => 0x23, "PAGEUP" => 0x21, "PAGEDOWN" => 0x22,
        "INSERT" => 0x2D,
        "F1" => 0x70, "F2" => 0x71, "F3" => 0x72, "F4" => 0x73,
        "F5" => 0x74, "F6" => 0x75, "F7" => 0x76, "F8" => 0x77,
        "F9" => 0x78, "F10" => 0x79, "F11" => 0x7A, "F12" => 0x7B,
        _ => (ushort)0
    };
}

#if WINDOWS
public static class ExecutorComandos
{
    [DllImport("user32.dll")] private static extern bool LockWorkStation();
    [DllImport("user32.dll", SetLastError = true)] private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    private static readonly IntPtr HWND_BROADCAST = new(0xffff);
    private const uint WM_SYSCOMMAND = 0x0112;
    private static readonly IntPtr SC_MONITORPOWER = new(0xF170);

    public static void Executar(MensagemRede m)
    {
        if (m.Tipo == "power") { ExecutarEnergia(m.Acao); return; }
        Aplicar(TradutorEntrada.Traduzir(m));
    }

    private static void Aplicar(InstrucaoEntrada instrucao)
    {
        switch (instrucao.Acao)
        {
            case AcaoEntrada.MoverRelativo: EntradaWindows.Mover(instrucao.X, instrucao.Y); break;
            case AcaoEntrada.MoverAbsoluto: EntradaWindows.MoverAbsoluto(instrucao.X, instrucao.Y, instrucao.Monitor); break;
            case AcaoEntrada.Clicar: EntradaWindows.Clique(instrucao.Botao, instrucao.Cliques); break;
            case AcaoEntrada.PressionarBotao: EntradaWindows.BotaoBaixar(instrucao.Botao); break;
            case AcaoEntrada.SoltarBotao: EntradaWindows.BotaoSoltar(instrucao.Botao); break;
            case AcaoEntrada.Rolar: EntradaWindows.Scroll(instrucao.Delta, instrucao.Eixo); break;
            case AcaoEntrada.Digitar: EntradaWindows.Texto(instrucao.Texto); break;
            case AcaoEntrada.Teclar: EntradaWindows.AcionarTeclas(instrucao.SequenciaDeTeclas()); break;
        }
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
#endif
