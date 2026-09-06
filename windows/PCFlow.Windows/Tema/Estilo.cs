using System.Windows;
using System.Windows.Media;
// WPF e WinForms convivem neste projeto (a bandeja usa WinForms), então Brush e
// Color precisam ser desambiguados explicitamente.
using Brush = System.Windows.Media.Brush;
using Color = System.Windows.Media.Color;

namespace PCFlow.Windows.Tema;

/// <summary>
/// Propriedades anexadas do sistema visual do PCFlow.
///
/// Existem para que um único <c>ControlTemplate</c> de botão atenda a todas as
/// variantes (primário, secundário, perigo, ícone, navegação). Sem elas cada
/// variante precisaria do seu próprio template só para trocar a cor de passagem
/// do mouse, e a manutenção sairia de controle.
/// </summary>
public static class Estilo
{
    private static SolidColorBrush Fixo(uint argb)
    {
        var pincel = new SolidColorBrush(Color.FromArgb(
            (byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb));
        pincel.Freeze();
        return pincel;
    }

    private static readonly Brush SuperficieElevada = Fixo(0xFF1C232C);
    private static readonly Brush Borda = Fixo(0xFF262D37);
    private static readonly Brush Acao = Fixo(0xFFF2AA2E);

    // ---------------- aparência ----------------

    /// <summary>Fundo enquanto o ponteiro está sobre o controle.</summary>
    public static readonly DependencyProperty FundoHoverProperty =
        DependencyProperty.RegisterAttached("FundoHover", typeof(Brush), typeof(Estilo),
            new FrameworkPropertyMetadata(SuperficieElevada));

    public static Brush GetFundoHover(DependencyObject alvo) => (Brush)alvo.GetValue(FundoHoverProperty);
    public static void SetFundoHover(DependencyObject alvo, Brush valor) => alvo.SetValue(FundoHoverProperty, valor);

    /// <summary>Fundo enquanto o controle está pressionado.</summary>
    public static readonly DependencyProperty FundoPressionadoProperty =
        DependencyProperty.RegisterAttached("FundoPressionado", typeof(Brush), typeof(Estilo),
            new FrameworkPropertyMetadata(Borda));

    public static Brush GetFundoPressionado(DependencyObject alvo) => (Brush)alvo.GetValue(FundoPressionadoProperty);
    public static void SetFundoPressionado(DependencyObject alvo, Brush valor) => alvo.SetValue(FundoPressionadoProperty, valor);

    /// <summary>Cor do anel de foco de teclado. Dourado por padrão.</summary>
    public static readonly DependencyProperty CorFocoProperty =
        DependencyProperty.RegisterAttached("CorFoco", typeof(Brush), typeof(Estilo),
            new FrameworkPropertyMetadata(Acao));

    public static Brush GetCorFoco(DependencyObject alvo) => (Brush)alvo.GetValue(CorFocoProperty);
    public static void SetCorFoco(DependencyObject alvo, Brush valor) => alvo.SetValue(CorFocoProperty, valor);

    /// <summary>Raio do canto. A escala do contrato é 8 / 14 / 20 / 999.</summary>
    public static readonly DependencyProperty RaioProperty =
        DependencyProperty.RegisterAttached("Raio", typeof(CornerRadius), typeof(Estilo),
            new FrameworkPropertyMetadata(new CornerRadius(14)));

    public static CornerRadius GetRaio(DependencyObject alvo) => (CornerRadius)alvo.GetValue(RaioProperty);
    public static void SetRaio(DependencyObject alvo, CornerRadius valor) => alvo.SetValue(RaioProperty, valor);

    // ---------------- conteúdo ----------------

    /// <summary>Desenho vetorial exibido antes do rótulo. Todos os ícones do app vivem em Tema/Icones.xaml.</summary>
    public static readonly DependencyProperty IconeProperty =
        DependencyProperty.RegisterAttached("Icone", typeof(Geometry), typeof(Estilo),
            new FrameworkPropertyMetadata(null));

    public static Geometry? GetIcone(DependencyObject alvo) => (Geometry?)alvo.GetValue(IconeProperty);
    public static void SetIcone(DependencyObject alvo, Geometry? valor) => alvo.SetValue(IconeProperty, valor);

    /// <summary>Item de navegação selecionado: fundo dourado sutil e marca à esquerda.</summary>
    public static readonly DependencyProperty AtivoProperty =
        DependencyProperty.RegisterAttached("Ativo", typeof(bool), typeof(Estilo),
            new FrameworkPropertyMetadata(false));

    public static bool GetAtivo(DependencyObject alvo) => (bool)alvo.GetValue(AtivoProperty);
    public static void SetAtivo(DependencyObject alvo, bool valor) => alvo.SetValue(AtivoProperty, valor);

    /// <summary>Janela estreita: o item de navegação esconde o rótulo e centraliza o ícone.</summary>
    public static readonly DependencyProperty CompactoProperty =
        DependencyProperty.RegisterAttached("Compacto", typeof(bool), typeof(Estilo),
            new FrameworkPropertyMetadata(false));

    public static bool GetCompacto(DependencyObject alvo) => (bool)alvo.GetValue(CompactoProperty);
    public static void SetCompacto(DependencyObject alvo, bool valor) => alvo.SetValue(CompactoProperty, valor);
}
