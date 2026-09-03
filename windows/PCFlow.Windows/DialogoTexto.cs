using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace PCFlow.Windows;

/// <summary>Caixa de entrada simples (o WPF não traz uma pronta).</summary>
public static class DialogoTexto
{
    public static string? Perguntar(Window dono, string titulo, string pergunta, string valorInicial = "")
    {
        var caixa = new TextBox
        {
            Text = valorInicial,
            Margin = new Thickness(0, 12, 0, 16),
            Padding = new Thickness(8, 6, 8, 6),
            Background = new SolidColorBrush(Color.FromRgb(0x11, 0x16, 0x1C)),
            Foreground = new SolidColorBrush(Color.FromRgb(0xED, 0xF1, 0xF5)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x2B, 0x32, 0x3C)),
            CaretBrush = new SolidColorBrush(Color.FromRgb(0xED, 0xF1, 0xF5))
        };

        var ok = new Button { Content = "Salvar", Width = 96, IsDefault = true, Margin = new Thickness(0, 0, 8, 0) };
        var cancelar = new Button { Content = "Cancelar", Width = 96, IsCancel = true };

        var painel = new StackPanel { Margin = new Thickness(20) };
        painel.Children.Add(new TextBlock
        {
            Text = pergunta,
            TextWrapping = TextWrapping.Wrap,
            Foreground = new SolidColorBrush(Color.FromRgb(0xED, 0xF1, 0xF5))
        });
        painel.Children.Add(caixa);
        var linha = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        linha.Children.Add(ok);
        linha.Children.Add(cancelar);
        painel.Children.Add(linha);

        var janela = new Window
        {
            Title = titulo,
            Content = painel,
            Owner = dono,
            Width = 400,
            SizeToContent = SizeToContent.Height,
            ResizeMode = ResizeMode.NoResize,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Background = new SolidColorBrush(Color.FromRgb(0x15, 0x1A, 0x21)),
            ShowInTaskbar = false
        };

        string? resultado = null;
        ok.Click += (_, _) => { resultado = caixa.Text; janela.DialogResult = true; };
        cancelar.Click += (_, _) => janela.DialogResult = false;
        janela.Loaded += (_, _) => { caixa.Focus(); caixa.SelectAll(); };
        janela.ShowDialog();
        return resultado;
    }
}
