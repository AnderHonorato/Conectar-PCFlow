using System;
using System.Net;

namespace FlowTutum.Next.Security;

public sealed record ResultadoDeConexao(int CodigoHttp, string Mensagem, string? Token = null)
{
    public bool Sucesso => CodigoHttp == 200 && Token is not null;
}

/// <summary>
/// Camada de aplicação do acesso doméstico. Pronta para ser chamada pelo
/// endpoint /api/local-connect existente quando o programa da AS voltar online.
/// </summary>
public sealed class ServicoDeAcessoLocal
{
    private readonly PoliticaRedeLocal _politica;
    private readonly SessoesRedeLocal _sessoes;
    private readonly LimitadorDeConexoes _limite;

    public ServicoDeAcessoLocal(
        PoliticaRedeLocal politica, SessoesRedeLocal sessoes, LimitadorDeConexoes limite)
    {
        _politica = politica;
        _sessoes = sessoes;
        _limite = limite;
    }

    public ResultadoDeConexao Conectar(
        IPAddress? remoto, IPAddress? destino, string? origem, string esquema,
        string autoridade, DateTimeOffset agora)
    {
        if (!_sessoes.Ativo) return new(403, "Acesso direto desligado no Windows.");
        if (!_politica.EhPermitido(remoto, destino))
            return new(403, "Conexão fora da rede doméstica.");
        if (!PoliticaRedeLocal.OrigemPermitida(origem, esquema, autoridade))
            return new(403, "Origem não autorizada.");
        if (remoto is null) return new(403, "IP não informado.");
        if (!_limite.Permitir(remoto, agora))
            return new(429, "Muitas tentativas. Tente novamente após um minuto.");

        var token = _sessoes.Emitir(remoto, agora);
        return token is null
            ? new(503, "Limite de sessões temporárias atingido.")
            : new(200, "Conexão local autorizada.", token);
    }

    public bool PodeControlar(string? token, IPAddress? remoto, IPAddress? destino, DateTimeOffset agora) =>
        _politica.EhPermitido(remoto, destino) && _sessoes.Verificar(token, remoto, agora);

    // Acionado pelo ícone da bandeja Windows. Revoga até as conexões já abertas.
    public void DefinirAcessoDireto(bool permitir) => _sessoes.DefinirAtivo(permitir);
}
