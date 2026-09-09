namespace PCFlow.Windows.Logica;

/// <summary>Faixas de largura da janela. Cada uma esconde algo que não cabe.</summary>
public enum FaixaJanela
{
    /// <summary>Barra lateral só com ícone; painel de detalhe fecha.</summary>
    Estreita,

    /// <summary>Barra lateral inteira; painel de detalhe ainda fechado.</summary>
    Media,

    /// <summary>Tudo visível, incluindo o painel de detalhe e o QR.</summary>
    Larga
}

/// <summary>
/// Cálculo do tamanho e do layout da janela.
///
/// Isto já corrigiu um defeito real: a janela nascia com 1280x800 e mínimo de
/// 1040x680, e num notebook de 1366x768 a 125% a área útil fica perto de
/// 1092x568 — a janela nascia maior que a tela e o mínimo impedia encolher.
/// A regra agora é sempre caber na área de trabalho de verdade.
/// Sem WPF aqui de propósito: assim dá para testar em qualquer sistema.
/// </summary>
public static class TamanhoResponsivo
{
    public const double LarguraDesejada = 1180;
    public const double AlturaDesejada = 760;
    public const double LarguraMinimaDesejada = 640;
    public const double AlturaMinimaDesejada = 460;

    public static DimensoesJanela Calcular(
        double areaEsquerda, double areaTopo, double areaLargura, double areaAltura,
        double larguraSalva, double alturaSalva)
    {
        if (areaLargura <= 0 || areaAltura <= 0)
            return new DimensoesJanela(LarguraDesejada, AlturaDesejada, areaEsquerda, areaTopo,
                LarguraMinimaDesejada, AlturaMinimaDesejada);

        var minLargura = Math.Min(LarguraMinimaDesejada, Math.Max(380, areaLargura - 40));
        var minAltura = Math.Min(AlturaMinimaDesejada, Math.Max(320, areaAltura - 40));

        var largura = larguraSalva > 0 ? larguraSalva : LarguraDesejada;
        var altura = alturaSalva > 0 ? alturaSalva : AlturaDesejada;

        largura = Math.Max(minLargura, Math.Min(largura, areaLargura - 20));
        altura = Math.Max(minAltura, Math.Min(altura, areaAltura - 20));

        var esquerda = areaEsquerda + Math.Max(0, (areaLargura - largura) / 2);
        var topo = areaTopo + Math.Max(0, (areaAltura - altura) / 2);
        return new DimensoesJanela(largura, altura, esquerda, topo, minLargura, minAltura);
    }

    public static FaixaJanela Faixa(double largura) => largura switch
    {
        < 900 => FaixaJanela.Estreita,
        < 1160 => FaixaJanela.Media,
        _ => FaixaJanela.Larga
    };

    /// <summary>Largura da barra lateral: só ícone quando a janela é estreita.</summary>
    public static double LarguraBarraLateral(FaixaJanela faixa)
        => faixa == FaixaJanela.Estreita ? 72 : 216;

    /// <summary>Largura do painel de detalhe; 0 quando não cabe.</summary>
    public static double LarguraPainelDetalhe(FaixaJanela faixa)
        => faixa == FaixaJanela.Larga ? 320 : 0;
}

public sealed record DimensoesJanela(
    double Largura, double Altura, double Esquerda, double Topo,
    double LarguraMinima, double AlturaMinima);
