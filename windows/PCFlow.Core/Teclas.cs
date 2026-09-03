namespace PCFlow.Core;

/// <summary>
/// Tradução de nomes de tecla do protocolo para Virtual-Key Codes do Windows.
/// Mantido no núcleo (sem P/Invoke) para poder ser testado em qualquer plataforma.
/// </summary>
public static class Teclas
{
    private static readonly Dictionary<string, ushort> Mapa = new(StringComparer.OrdinalIgnoreCase)
    {
        ["BACKSPACE"] = 0x08, ["TAB"] = 0x09, ["CLEAR"] = 0x0C, ["ENTER"] = 0x0D,
        ["SHIFT"] = 0x10, ["CTRL"] = 0x11, ["ALT"] = 0x12, ["PAUSE"] = 0x13,
        ["CAPSLOCK"] = 0x14, ["ESC"] = 0x1B, ["SPACE"] = 0x20,
        ["PAGEUP"] = 0x21, ["PAGEDOWN"] = 0x22, ["END"] = 0x23, ["HOME"] = 0x24,
        ["LEFT"] = 0x25, ["UP"] = 0x26, ["RIGHT"] = 0x27, ["DOWN"] = 0x28,
        ["PRINTSCREEN"] = 0x2C, ["INSERT"] = 0x2D, ["DELETE"] = 0x2E,
        ["WIN"] = 0x5B, ["MENU"] = 0x5D, ["APPS"] = 0x5D,
        ["NUMLOCK"] = 0x90, ["SCROLLLOCK"] = 0x91,
        ["F1"] = 0x70, ["F2"] = 0x71, ["F3"] = 0x72, ["F4"] = 0x73,
        ["F5"] = 0x74, ["F6"] = 0x75, ["F7"] = 0x76, ["F8"] = 0x77,
        ["F9"] = 0x78, ["F10"] = 0x79, ["F11"] = 0x7A, ["F12"] = 0x7B,
        ["VOLUMEMUTE"] = 0xAD, ["VOLUMEDOWN"] = 0xAE, ["VOLUMEUP"] = 0xAF,
        ["NEXTTRACK"] = 0xB0, ["PREVTRACK"] = 0xB1, ["STOPMEDIA"] = 0xB2, ["PLAYPAUSE"] = 0xB3,
        ["BROWSERBACK"] = 0xA6, ["BROWSERFORWARD"] = 0xA7, ["BROWSERREFRESH"] = 0xA8,
        ["BROWSERHOME"] = 0xAC, ["BROWSERSEARCH"] = 0xAA,
    };

    private static readonly Dictionary<string, ushort> Modificadores = new(StringComparer.OrdinalIgnoreCase)
    {
        ["CTRL"] = 0x11, ["CONTROL"] = 0x11,
        ["SHIFT"] = 0x10,
        ["ALT"] = 0x12,
        ["WIN"] = 0x5B, ["META"] = 0x5B, ["SUPER"] = 0x5B,
    };

    /// <summary>Converte um nome de tecla em VK. Aceita "A".."Z", "0".."9" e nomes do mapa.</summary>
    public static ushort? Resolver(string? nome)
    {
        if (string.IsNullOrWhiteSpace(nome)) return null;
        nome = nome.Trim();
        if (Mapa.TryGetValue(nome, out var vk)) return vk;
        if (nome.Length == 1)
        {
            var c = char.ToUpperInvariant(nome[0]);
            if (c is >= 'A' and <= 'Z') return (ushort)c;
            if (c is >= '0' and <= '9') return (ushort)c;
        }
        return null;
    }

    public static ushort? ResolverModificador(string? nome)
        => nome is not null && Modificadores.TryGetValue(nome.Trim(), out var vk) ? vk : null;

    /// <summary>
    /// Interpreta combinações no formato "ctrl+shift+s" devolvendo os modificadores e a tecla final.
    /// </summary>
    public static (List<ushort> Mods, ushort? Tecla) InterpretarCombo(string combo)
    {
        var mods = new List<ushort>();
        ushort? principal = null;
        foreach (var parte in combo.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var mod = ResolverModificador(parte);
            if (mod is not null && !mods.Contains(mod.Value)) mods.Add(mod.Value);
            else principal = Resolver(parte) ?? principal;
        }
        return (mods, principal);
    }

    /// <summary>Ações de mídia aceitas mapeadas para a tecla multimídia correspondente.</summary>
    public static ushort? ResolverMidia(string? acao) => acao?.ToLowerInvariant() switch
    {
        "playpause" or "play" or "pause" => 0xB3,
        "next" or "proxima" => 0xB0,
        "previous" or "anterior" => 0xB1,
        "stop" or "parar" => 0xB2,
        "volumeup" or "volumemais" => 0xAF,
        "volumedown" or "volumemenos" => 0xAE,
        "mute" or "mudo" => 0xAD,
        _ => null
    };
}
