using System.Windows;
using System.Windows.Media;
using Microsoft.Win32;
using WpfApplication = System.Windows.Application;
using WpfColor = System.Windows.Media.Color;
using WpfColorConverter = System.Windows.Media.ColorConverter;

namespace PCFlow.Windows.Tema;

public enum ModoTema { Escuro, Claro, Automatico }

/// <summary>
/// Troca de tema em tempo de execução.
///
/// Funciona porque nenhum estilo declara cor literal: todos pedem o pincel por
/// <c>DynamicResource</c>. Trocar o dicionário de paleta na posição 0 dos
/// recursos do aplicativo repinta a interface inteira, inclusive janelas já
/// abertas. A cor de destaque entra por cima, no dicionário próprio do
/// aplicativo, que vence os mesclados.
/// </summary>
public static class GerenciadorTema
{
    /// <summary>As cinco opções de destaque oferecidas na tela Configurações.</summary>
    public static readonly IReadOnlyList<OpcaoDestaque> Destaques =
    [
        new("Dourado", "#F2AA2E"),
        new("Turquesa", "#14D3C3"),
        new("Azul", "#5B9DFF"),
        new("Violeta", "#B98BFF"),
        new("Verde", "#57C97A")
    ];

    public static ModoTema ModoAtual { get; private set; } = ModoTema.Escuro;
    public static string DestaqueAtual { get; private set; } = "#F2AA2E";

    /// <summary>Avisa quem precisa se redesenhar por conta própria (a prévia, por exemplo).</summary>
    public static event Action? Alterado;

    public static ModoTema LerModo(string? texto) => (texto ?? "").ToLowerInvariant() switch
    {
        "claro" => ModoTema.Claro,
        "automatico" => ModoTema.Automatico,
        _ => ModoTema.Escuro
    };

    public static string EscreverModo(ModoTema modo) => modo switch
    {
        ModoTema.Claro => "claro",
        ModoTema.Automatico => "automatico",
        _ => "escuro"
    };

    public static void Aplicar(ModoTema modo, string destaqueHex)
    {
        ModoAtual = modo;
        DestaqueAtual = Valido(destaqueHex) ? destaqueHex : "#F2AA2E";

        var app = WpfApplication.Current;
        if (app is null) return;

        var claro = modo == ModoTema.Claro || (modo == ModoTema.Automatico && SistemaEstaClaro());
        var caminho = claro ? "Tema/PaletaClara.xaml" : "Tema/PaletaEscura.xaml";
        var paleta = new ResourceDictionary { Source = new Uri(caminho, UriKind.Relative) };

        if (app.Resources.MergedDictionaries.Count == 0) app.Resources.MergedDictionaries.Add(paleta);
        else app.Resources.MergedDictionaries[0] = paleta;

        AplicarDestaque(app, DestaqueAtual, claro);
        Alterado?.Invoke();
    }

    private static void AplicarDestaque(WpfApplication app, string hex, bool temaClaro)
    {
        var cor = (WpfColor)WpfColorConverter.ConvertFromString(hex)!;

        // No tema claro o acento precisa escurecer, senão texto dourado sobre
        // branco fica ilegível — é a única diferença entre as duas paletas.
        var baseCor = temaClaro ? Escurecer(cor, 0.22) : cor;

        app.Resources["Acao"] = Congelado(baseCor);
        app.Resources["AcaoPressionada"] = Congelado(Escurecer(baseCor, 0.12));
        app.Resources["AcaoSuave"] = Congelado(WpfColor.FromArgb(0x26, baseCor.R, baseCor.G, baseCor.B));
        app.Resources["TextoSobreAcao"] = Congelado(Luminancia(baseCor) > 0.55
            ? WpfColor.FromRgb(0x10, 0x16, 0x1C)
            : WpfColor.FromRgb(0xFF, 0xFF, 0xFF));
    }

    private static bool Valido(string? hex)
    {
        if (string.IsNullOrWhiteSpace(hex)) return false;
        try { _ = WpfColorConverter.ConvertFromString(hex); return true; }
        catch (FormatException) { return false; }
    }

    /// <summary>Lê a preferência de tema do próprio Windows. Sem chave, assume escuro.</summary>
    public static bool SistemaEstaClaro()
    {
        try
        {
            var valor = Registry.GetValue(
                @"HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize",
                "AppsUseLightTheme", 0);
            return valor is int numero && numero != 0;
        }
        catch (Exception) { return false; }
    }

    private static SolidColorBrush Congelado(WpfColor cor)
    {
        var pincel = new SolidColorBrush(cor);
        pincel.Freeze();
        return pincel;
    }

    private static WpfColor Escurecer(WpfColor cor, double fator) => WpfColor.FromRgb(
        (byte)(cor.R * (1 - fator)), (byte)(cor.G * (1 - fator)), (byte)(cor.B * (1 - fator)));

    private static double Luminancia(WpfColor cor)
        => (0.299 * cor.R + 0.587 * cor.G + 0.114 * cor.B) / 255.0;

    /// <summary>
    /// Duração de abertura de diálogo, já respeitando a redução de animações
    /// do Windows. Com a redução ligada, a transição vira troca imediata.
    /// </summary>
    public static TimeSpan DuracaoDialogo =>
        SystemParameters.ClientAreaAnimation ? TimeSpan.FromMilliseconds(200) : TimeSpan.Zero;

    public static TimeSpan DuracaoControle =>
        SystemParameters.ClientAreaAnimation ? TimeSpan.FromMilliseconds(140) : TimeSpan.Zero;
}

public sealed record OpcaoDestaque(string Nome, string Hex);
