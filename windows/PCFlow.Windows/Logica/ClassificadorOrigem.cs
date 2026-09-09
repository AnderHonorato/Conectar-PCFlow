using System.Net;
using System.Net.Sockets;

namespace PCFlow.Windows.Logica;

/// <summary>
/// De onde uma solicitação de conexão está vindo, a partir do texto que o
/// servidor entrega em <c>SolicitacaoConexao.EnderecoIp</c>.
///
/// A janela de aceite precisa dizer isso em letras grandes: aceitar alguém da
/// sua própria rede é uma decisão bem diferente de aceitar alguém da internet.
/// </summary>
public static class ClassificadorOrigem
{
    public static OrigemLegivel Classificar(string? endereco)
    {
        var texto = (endereco ?? "").Trim();
        if (texto.Length == 0)
            return new OrigemLegivel("Origem desconhecida", "Não foi possível identificar de onde vem a conexão.", false, false);

        if (texto.Contains("retransmiss", StringComparison.OrdinalIgnoreCase) ||
            texto.Contains("servidor", StringComparison.OrdinalIgnoreCase))
            return new OrigemLegivel("Pelo servidor de retransmissão",
                "A conexão chega por fora da sua rede, emendada pelo servidor. O conteúdo continua cifrado de ponta a ponta.",
                false, true);

        var so = texto;
        var corte = so.LastIndexOf(':');
        if (corte > 0 && so.Count(c => c == ':') == 1) so = so[..corte];
        so = so.Trim('[', ']');

        if (!IPAddress.TryParse(so, out var ip))
            return new OrigemLegivel($"Origem: {texto}", "Endereço não reconhecido como rede local.", false, false);

        if (EhRedeLocal(ip))
            return new OrigemLegivel("Na sua rede local",
                $"O aparelho está no mesmo Wi-Fi ou cabo que este computador ({ip}).", true, false);

        return new OrigemLegivel("Pela internet",
            $"A conexão vem de fora da sua rede ({ip}). Só aceite se você reconhece quem está pedindo.",
            false, false);
    }

    public static bool EhRedeLocal(IPAddress endereco)
    {
        if (IPAddress.IsLoopback(endereco) || endereco.IsIPv6LinkLocal) return true;
        if (endereco.IsIPv4MappedToIPv6) endereco = endereco.MapToIPv4();
        if (endereco.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = endereco.GetAddressBytes();
        return b[0] == 10 || b[0] == 127 || (b[0] == 192 && b[1] == 168) ||
               (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 169 && b[1] == 254);
    }
}

/// <param name="Titulo">Frase curta que vai na janela de aceite.</param>
/// <param name="Explicacao">Uma linha dizendo o que isso significa.</param>
public sealed record OrigemLegivel(string Titulo, string Explicacao, bool RedeLocal, bool ViaServidor);
