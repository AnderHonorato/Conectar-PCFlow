using System.Globalization;
using System.Windows;
using System.Windows.Data;
// WPF e WinForms convivem neste projeto: Binding e ColorConverter existem nos dois.
using WpfBinding = System.Windows.Data.Binding;

namespace PCFlow.Windows.Tema;

/// <summary>Inverte um booleano (para habilitar o oposto de uma opção).</summary>
public sealed class ConversorBoolInvertido : IValueConverter
{
    public object Convert(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => valor is bool b && !b;

    public object ConvertBack(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => valor is bool b && !b;
}

/// <summary>Verdadeiro esconde, falso mostra.</summary>
public sealed class ConversorBoolVisibilidadeInvertida : IValueConverter
{
    public object Convert(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => valor is bool b && b ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => valor is Visibility v && v != Visibility.Visible;
}

/// <summary>Nulo esconde. Usado pelos templates para o ícone opcional do botão.</summary>
public sealed class ConversorNuloParaVisibilidade : IValueConverter
{
    public object Convert(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => valor is null ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => WpfBinding.DoNothing;
}

/// <summary>Texto vazio esconde o rótulo em vez de deixar um espaço morto.</summary>
public sealed class ConversorTextoParaVisibilidade : IValueConverter
{
    public object Convert(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => string.IsNullOrWhiteSpace(valor as string) ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => WpfBinding.DoNothing;
}

/// <summary>
/// Permissão concedida sai em texto normal; permissão negada sai apagada.
/// A cor vem da paleta em tempo de execução, então segue a troca de tema.
/// </summary>
public sealed class ConversorPermissaoPincel : IValueConverter
{
    public object Convert(object? valor, Type destino, object? parametro, CultureInfo cultura)
    {
        var chave = valor is bool permitido && permitido ? "Texto" : "TextoDesabilitado";
        return System.Windows.Application.Current?.TryFindResource(chave) ?? System.Windows.Media.Brushes.Gray;
    }

    public object ConvertBack(object? valor, Type destino, object? parametro, CultureInfo cultura)
        => WpfBinding.DoNothing;
}
