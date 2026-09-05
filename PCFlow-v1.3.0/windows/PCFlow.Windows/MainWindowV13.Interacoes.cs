using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace PCFlow.Windows;

public partial class MainWindowV13
{
    private bool _interacoesV13Ligadas;

    protected override void OnContentRendered(EventArgs e)
    {
        base.OnContentRendered(e);
        if (_interacoesV13Ligadas) return;
        _interacoesV13Ligadas = true;

        RemotoEntrada.Checked += PermissaoRemota_Changed;
        RemotoEntrada.Unchecked += PermissaoRemota_Changed;
        RemotoClipboard.Checked += PermissaoRemota_Changed;
        RemotoClipboard.Unchecked += PermissaoRemota_Changed;
        RemotoArquivos.Checked += PermissaoRemota_Changed;
        RemotoArquivos.Unchecked += PermissaoRemota_Changed;
        RemotoEnergia.Checked += PermissaoRemota_Changed;
        RemotoEnergia.Unchecked += PermissaoRemota_Changed;

        ConfigIdioma.SelectionChanged += Idioma_Changed;
        PreviewKeyDown += AtalhoGlobalV13_KeyDown;
    }

    private void PermissaoRemota_Changed(object sender, RoutedEventArgs e)
    {
        if (_carregandoUi) return;
        var c = _servidor.Configuracao;
        c.PermitirEntrada = RemotoEntrada.IsChecked == true;
        c.PermitirClipboard = RemotoClipboard.IsChecked == true;
        c.PermitirArquivos = RemotoArquivos.IsChecked == true;
        c.PermitirEnergia = RemotoEnergia.IsChecked == true;

        // Mantém as duas telas de configuração sincronizadas.
        CheckEntrada.IsChecked = c.PermitirEntrada;
        CheckClipboard.IsChecked = c.PermitirClipboard;
        CheckArquivos.IsChecked = c.PermitirArquivos;
        CheckEnergia.IsChecked = c.PermitirEnergia;
        _servidor.SalvarConfiguracao();
    }

    private void Idioma_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_carregandoUi) return;
        var tag = (ConfigIdioma.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "pt-BR";
        _servidor.Configuracao.Idioma = tag;
        _servidor.SalvarConfiguracao();

        // A arquitetura principal permanece PT-BR; os itens de navegação e títulos
        // mudam imediatamente para deixar a preferência funcional sem exigir reinício.
        if (tag == "en")
        {
            MenuInicio.Content = "⌂   Home";
            MenuDispositivos.Content = "▣   Devices";
            MenuSeguranca.Content = "♢   Security";
            MenuRemoto.Content = "⇄   Remote Access";
            MenuTransferencia.Content = "↔   Transfer";
            MenuConfiguracoes.Content = "⚙   Settings";
        }
        else if (tag == "es")
        {
            MenuInicio.Content = "⌂   Inicio";
            MenuDispositivos.Content = "▣   Dispositivos";
            MenuSeguranca.Content = "♢   Seguridad";
            MenuRemoto.Content = "⇄   Acceso remoto";
            MenuTransferencia.Content = "↔   Transferencia";
            MenuConfiguracoes.Content = "⚙   Configuración";
        }
        else
        {
            MenuInicio.Content = "⌂   Início";
            MenuDispositivos.Content = "▣   Dispositivos";
            MenuSeguranca.Content = "♢   Segurança";
            MenuRemoto.Content = "⇄   Acesso Remoto";
            MenuTransferencia.Content = "↔   Transferência";
            MenuConfiguracoes.Content = "⚙   Configurações";
        }
    }

    private void AtalhoGlobalV13_KeyDown(object sender, KeyEventArgs e)
    {
        var ctrl = Keyboard.Modifiers.HasFlag(ModifierKeys.Control);
        var shift = Keyboard.Modifiers.HasFlag(ModifierKeys.Shift);

        if (ctrl && !shift && e.Key == Key.N)
        {
            AdicionarDispositivo_Click(this, new RoutedEventArgs());
            e.Handled = true;
            return;
        }

        if (ctrl && shift && e.Key == Key.P)
        {
            Restaurar();
            NavegarPara("inicio");
            e.Handled = true;
        }
    }
}
