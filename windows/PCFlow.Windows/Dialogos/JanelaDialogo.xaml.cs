using System.Windows;
using System.Windows.Input;
using PCFlow.Windows.Tema;
using WpfWindow = System.Windows.Window;
// WPF e WinForms convivem: KeyEventArgs e MouseButtonEventArgs existem nos dois.
using WpfKeyEventArgs = System.Windows.Input.KeyEventArgs;
using WpfMouseButtonEventArgs = System.Windows.Input.MouseButtonEventArgs;

namespace PCFlow.Windows.Dialogos;

public enum EstiloDialogo { Informacao, Sucesso, Aviso, Perigo }

/// <summary>
/// O diálogo do PCFlow: painel próprio, véu escuro por trás e nenhuma borda do
/// sistema. Substitui <c>MessageBox</c> em todo o aplicativo — o contrato v2
/// não admite caixa nativa em nenhuma das duas plataformas.
/// </summary>
public partial class JanelaDialogo : WpfWindow
{
    private bool _destrutiva;
    private Func<string, string?>? _validar;
    private bool _pedindoTexto;
    private bool _usarSenha;

    public JanelaDialogo()
    {
        InitializeComponent();
        PreviewKeyDown += AoTeclar;
        Loaded += (_, _) => Cortina.Abrir(this, Subida);
    }

    public string? TextoInformado { get; private set; }

    private void Configurar(EstiloDialogo estilo)
    {
        var (pincel, fundo, icone) = estilo switch
        {
            EstiloDialogo.Sucesso => ("Seguro", "SeguroSuave", "IconeCheck"),
            EstiloDialogo.Aviso => ("Acao", "AcaoSuave", "IconeAviso"),
            EstiloDialogo.Perigo => ("Erro", "ErroSuave", "IconeAviso"),
            _ => ("Acao", "AcaoSuave", "IconeInfo")
        };
        MarcaIcone.SetResourceReference(System.Windows.Shapes.Shape.StrokeProperty, pincel);
        MarcaFundo.SetResourceReference(System.Windows.Controls.Border.BackgroundProperty, fundo);
        MarcaIcone.SetResourceReference(System.Windows.Shapes.Path.DataProperty, icone);
    }

    // ------------------------------------------------------------------
    // Montagem de cada tipo
    // ------------------------------------------------------------------

    internal static JanelaDialogo Aviso(WpfWindow? dono, string titulo, string mensagem,
        string? detalhe, EstiloDialogo estilo, string botao)
    {
        var janela = Criar(dono);
        janela.Titulo.Text = titulo;
        janela.Mensagem.Text = mensagem;
        janela.BotaoConfirmar.Content = botao;
        janela.BotaoCancelar.Visibility = Visibility.Collapsed;
        janela.Configurar(estilo);
        janela.MostrarDetalhe(detalhe);
        return janela;
    }

    internal static JanelaDialogo Confirmacao(WpfWindow? dono, string titulo, string mensagem,
        string confirmar, string cancelar, bool destrutiva, string? detalhe)
    {
        var janela = Criar(dono);
        janela.Titulo.Text = titulo;
        janela.Mensagem.Text = mensagem;
        janela.BotaoConfirmar.Content = confirmar;
        janela.BotaoCancelar.Content = cancelar;
        janela._destrutiva = destrutiva;
        janela.Configurar(destrutiva ? EstiloDialogo.Perigo : EstiloDialogo.Aviso);
        janela.MostrarDetalhe(detalhe);

        if (destrutiva)
        {
            // Ação destrutiva nunca nasce em foco: o padrão é sair sem estragar nada.
            janela.BotaoConfirmar.Style = (Style)janela.FindResource("BotaoPerigo");
            janela.Loaded += (_, _) => janela.BotaoCancelar.Focus();
        }
        else janela.Loaded += (_, _) => janela.BotaoConfirmar.Focus();
        return janela;
    }

    internal static JanelaDialogo Texto(WpfWindow? dono, string titulo, string mensagem, string rotulo,
        string valorInicial, bool senha, string confirmar, Func<string, string?>? validar)
    {
        var janela = Criar(dono);
        janela.Titulo.Text = titulo;
        janela.Mensagem.Text = mensagem;
        janela.RotuloCampo.Text = rotulo;
        janela.BotaoConfirmar.Content = confirmar;
        janela.BotaoCancelar.Content = "Cancelar";
        janela.AreaCampo.Visibility = Visibility.Visible;
        janela._pedindoTexto = true;
        janela._usarSenha = senha;
        janela._validar = validar;
        janela.Configurar(EstiloDialogo.Informacao);

        if (senha)
        {
            janela.Campo.Visibility = Visibility.Collapsed;
            janela.CampoSenha.Visibility = Visibility.Visible;
            janela.Loaded += (_, _) => janela.CampoSenha.Focus();
        }
        else
        {
            janela.Campo.Text = valorInicial;
            janela.Loaded += (_, _) => { janela.Campo.Focus(); janela.Campo.SelectAll(); };
        }
        return janela;
    }

    private static JanelaDialogo Criar(WpfWindow? dono)
    {
        var janela = new JanelaDialogo();
        if (dono is not null && dono.IsVisible) janela.Owner = dono;
        Cortina.Posicionar(janela, dono);
        return janela;
    }

    private void MostrarDetalhe(string? detalhe)
    {
        if (string.IsNullOrWhiteSpace(detalhe)) return;
        Detalhe.Text = detalhe;
        CaixaDetalhe.Visibility = Visibility.Visible;
    }

    // ------------------------------------------------------------------
    // Interação
    // ------------------------------------------------------------------

    private void Confirmar_Click(object sender, RoutedEventArgs e)
    {
        if (_pedindoTexto)
        {
            var valor = _usarSenha ? CampoSenha.Password : Campo.Text;
            var erro = _validar?.Invoke(valor);
            if (erro is not null)
            {
                AvisoCampo.Text = erro;
                AvisoCampo.Visibility = Visibility.Visible;
                return;
            }
            TextoInformado = valor;
        }
        DialogResult = true;
    }

    private void Cancelar_Click(object sender, RoutedEventArgs e) => DialogResult = false;

    private void Campo_KeyDown(object sender, WpfKeyEventArgs e)
    {
        if (e.Key == Key.Enter) { Confirmar_Click(sender, e); e.Handled = true; }
    }

    private void AoTeclar(object sender, WpfKeyEventArgs e)
    {
        // Esc sempre sai sem confirmar: sair de um diálogo nunca é a ação destrutiva.
        if (e.Key == Key.Escape) { DialogResult = false; e.Handled = true; }
    }

    private void Veu_Click(object sender, WpfMouseButtonEventArgs e)
    {
        if (_destrutiva) return;
        DialogResult = false;
    }

    private void Painel_Click(object sender, WpfMouseButtonEventArgs e) => e.Handled = true;
}
