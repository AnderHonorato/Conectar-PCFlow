using PCFlow.Windows.Core;
using WpfWindow = System.Windows.Window;

namespace PCFlow.Windows.Dialogos;

/// <summary>
/// A única porta de entrada para avisar, confirmar e perguntar no PCFlow.
/// Nenhum arquivo do aplicativo chama <c>MessageBox</c>.
///
/// A API é assíncrona porque quem chama costuma estar num manipulador async ou
/// num evento vindo do servidor; por dentro, cada janela é modal de verdade
/// (<c>ShowDialog</c>), o que garante que nada da janela de trás seja clicado
/// enquanto a pergunta está na tela.
/// </summary>
public static class Dialogo
{
    public static Task AvisarAsync(WpfWindow? dono, string titulo, string mensagem,
        string? detalhe = null, EstiloDialogo estilo = EstiloDialogo.Informacao, string botao = "Entendi")
    {
        JanelaDialogo.Aviso(dono, titulo, mensagem, detalhe, estilo, botao).ShowDialog();
        return Task.CompletedTask;
    }

    public static Task<bool> ConfirmarAsync(WpfWindow? dono, string titulo, string mensagem,
        string confirmar = "Confirmar", string cancelar = "Cancelar",
        bool destrutiva = false, string? detalhe = null)
    {
        var janela = JanelaDialogo.Confirmacao(dono, titulo, mensagem, confirmar, cancelar, destrutiva, detalhe);
        return Task.FromResult(janela.ShowDialog() == true);
    }

    /// <param name="validar">Devolve a mensagem de erro, ou nulo quando o valor serve.</param>
    public static Task<string?> PedirTextoAsync(WpfWindow? dono, string titulo, string mensagem, string rotulo,
        string valorInicial = "", bool senha = false, string confirmar = "Salvar",
        Func<string, string?>? validar = null)
    {
        var janela = JanelaDialogo.Texto(dono, titulo, mensagem, rotulo, valorInicial, senha, confirmar, validar);
        return Task.FromResult(janela.ShowDialog() == true ? janela.TextoInformado : null);
    }

    /// <summary>
    /// A janela mais importante do produto: alguém pediu para entrar neste
    /// computador e a resposta é dada aqui.
    /// </summary>
    public static Task<bool> SolicitarConexaoAsync(WpfWindow? dono, SolicitacaoConexao solicitacao,
        PermissoesEfetivas permissoes)
    {
        var janela = new JanelaSolicitacaoConexao(dono, solicitacao, permissoes);
        return Task.FromResult(janela.ShowDialog() == true);
    }
}
