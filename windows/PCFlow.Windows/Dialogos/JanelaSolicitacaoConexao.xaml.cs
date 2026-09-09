using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using PCFlow.Windows.Core;
using PCFlow.Windows.Logica;
using WpfWindow = System.Windows.Window;

namespace PCFlow.Windows.Dialogos;

/// <summary>
/// A janela que decide se alguém entra neste computador.
///
/// Ela diz, sem rodeio: quem está pedindo, se já é conhecido, se vem da rede
/// local ou de fora, e exatamente o que vai poder fazer. Aceitar é dourado,
/// recusar é neutro, e recusar é sempre o que acontece se a janela for fechada.
/// </summary>
public partial class JanelaSolicitacaoConexao : WpfWindow
{
    public JanelaSolicitacaoConexao(WpfWindow? dono, SolicitacaoConexao solicitacao, PermissoesEfetivas permissoes)
    {
        InitializeComponent();
        if (dono is not null && dono.IsVisible) Owner = dono;
        Cortina.Posicionar(this, dono);

        NomeDispositivo.Text = solicitacao.Nome;
        Iniciais.Text = PadraoIdentidade.Iniciais(solicitacao.Nome);
        Miniatura.Background = Pincel(PadraoIdentidade.Cor(solicitacao.DispositivoId, 0), 0x33);
        Iniciais.Foreground = Pincel(PadraoIdentidade.Cor(solicitacao.DispositivoId, 0), 0xFF);

        AjustarConhecido(solicitacao.DispositivoConhecido);
        AjustarOrigem(solicitacao.EnderecoIp);

        ListaPermissoes.ItemsSource = new[]
        {
            Item("IconeDispositivos", "Ver a tela deste computador", permissoes.Tela),
            Item("IconePonteiro", "Usar o teclado e o mouse", permissoes.Entrada),
            Item("IconeAreaTransferencia", "Ler e escrever na área de transferência", permissoes.Clipboard),
            Item("IconePasta", "Enviar e baixar arquivos", permissoes.Arquivos),
            Item("IconeEnergia", "Desligar, reiniciar ou bloquear a tela", permissoes.Energia)
        };

        PreviewKeyDown += (_, e) =>
        {
            // Esc recusa. Fechar sem responder nunca pode virar um "sim".
            if (e.Key == Key.Escape) { DialogResult = false; e.Handled = true; }
        };
        Loaded += (_, _) =>
        {
            Cortina.Abrir(this, Subida);
            BotaoRecusar.Focus();
            Activate();
        };
    }

    private void AjustarConhecido(bool conhecido)
    {
        if (conhecido)
        {
            TextoConhecido.Text = "Dispositivo já conhecido";
            return;
        }
        TextoConhecido.Text = "Dispositivo novo";
        TextoConhecido.SetResourceReference(ForegroundProperty, "Acao");
        PilulaConhecido.SetResourceReference(System.Windows.Controls.Border.BackgroundProperty, "AcaoSuave");
    }

    private void AjustarOrigem(string endereco)
    {
        var origem = ClassificadorOrigem.Classificar(endereco);
        TextoOrigem.Text = origem.Titulo;
        Endereco.Text = origem.Titulo;
        ExplicacaoOrigem.Text = origem.Explicacao;

        if (origem.RedeLocal) return;

        // Fora da rede local o alerta sobe de tom: é o caso em que uma aceitação
        // distraída abre a máquina para a internet.
        TextoOrigem.SetResourceReference(ForegroundProperty, "Erro");
        PilulaOrigem.SetResourceReference(System.Windows.Controls.Border.BackgroundProperty, "ErroSuave");
        IconeOrigem.SetResourceReference(System.Windows.Shapes.Shape.StrokeProperty, "Erro");
        IconeOrigem.SetResourceReference(System.Windows.Shapes.Path.DataProperty, "IconeAviso");
    }

    private static ItemPermissao Item(string chaveIcone, string texto, bool permitido)
        => new((Geometry)System.Windows.Application.Current.FindResource(chaveIcone),
               permitido ? texto : $"{texto} — bloqueado neste computador", permitido);

    private static SolidColorBrush Pincel(string hex, byte alfa)
    {
        var cor = (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString(hex)!;
        var pincel = new SolidColorBrush(System.Windows.Media.Color.FromArgb(alfa, cor.R, cor.G, cor.B));
        pincel.Freeze();
        return pincel;
    }

    private void Aceitar_Click(object sender, RoutedEventArgs e) => DialogResult = true;
    private void Recusar_Click(object sender, RoutedEventArgs e) => DialogResult = false;
}

public sealed record ItemPermissao(Geometry Icone, string Texto, bool Permitido);
