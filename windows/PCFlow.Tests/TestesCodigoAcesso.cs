using System.Net;
using PCFlow.Windows.Core;
using Xunit;

namespace PCFlow.Tests;

/// <summary>
/// O código de acesso é o que substitui a descoberta quando o celular está em
/// outra rede. Se ele perder um byte no caminho, a conexão pela internet não
/// funciona — e pior, a pinagem do certificado deixaria de valer.
/// </summary>
public class TestesCodigoAcesso
{
    private const string Impressao =
        "3fa9c1d2e4b57608192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f80";

    [Fact]
    public void CodigoDiretoVoltaComEnderecoPortaEIdentidade()
    {
        var codigo = CodigoAcesso.GerarDireto(IPAddress.Parse("189.45.230.7"), 45456, Impressao);

        var destino = CodigoAcesso.Ler(codigo);

        Assert.NotNull(destino);
        Assert.False(destino!.ViaServidor);
        Assert.Equal("189.45.230.7", destino.Host);
        Assert.Equal(45456, destino.Porta);
        Assert.Equal(Impressao[..32], destino.ImpressaoTls);
    }

    [Fact]
    public void CodigoPorServidorVoltaComIdentificador()
    {
        var codigo = CodigoAcesso.GerarPorServidor(123456789, Impressao);

        var destino = CodigoAcesso.Ler(codigo);

        Assert.NotNull(destino);
        Assert.True(destino!.ViaServidor);
        Assert.Equal(123456789u, destino.IdentificadorServidor);
        Assert.Equal(Impressao[..32], destino.ImpressaoTls);
    }

    /// <summary>
    /// Quem dita o código pelo telefone troca O por 0 e I por 1 sem perceber.
    /// O alfabeto de Crockford existe justamente para isso não quebrar nada.
    /// </summary>
    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void TrocasComunsDeCaractereNaoQuebramOCodigo(bool minusculo)
    {
        var codigo = CodigoAcesso.GerarDireto(IPAddress.Parse("10.0.0.15"), 45456, Impressao);
        var digitado = codigo.Replace("0", "O").Replace("1", "I");
        if (minusculo) digitado = digitado.ToLowerInvariant();

        var destino = CodigoAcesso.Ler(digitado);

        Assert.NotNull(destino);
        Assert.Equal("10.0.0.15", destino!.Host);
        Assert.Equal(Impressao[..32], destino.ImpressaoTls);
    }

    [Fact]
    public void EspacosEHifensDigitadosAMaisSaoIgnorados()
    {
        var codigo = CodigoAcesso.GerarDireto(IPAddress.Parse("192.168.0.42"), 45456, Impressao);

        var destino = CodigoAcesso.Ler($"  {codigo.Replace("-", " ")}  ");

        Assert.Equal("192.168.0.42", destino?.Host);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("isso-nao-e-um-codigo")]
    [InlineData("ZZZZZ-ZZZZZ")]
    public void TextoQueNaoEhCodigoDevolveNulo(string texto) => Assert.Null(CodigoAcesso.Ler(texto));

    /// <summary>
    /// A impressão do código é truncada em 16 bytes para caber na tela; a que
    /// vem do certificado tem 32. Só pode fechar quando o prefixo é idêntico.
    /// </summary>
    [Fact]
    public void ImpressaoTruncadaConfereComACompleta() =>
        Assert.True(CodigoAcesso.ImpressaoConfere(Impressao, Impressao[..32]));

    [Fact]
    public void ImpressaoDeOutroCertificadoEhRecusada()
    {
        var outra = "00" + Impressao[2..];
        Assert.False(CodigoAcesso.ImpressaoConfere(outra, Impressao[..32]));
    }

    [Fact]
    public void ImpressaoCurtaDemaisEhRecusada() =>
        Assert.False(CodigoAcesso.ImpressaoConfere(Impressao, Impressao[..8]));

    [Fact]
    public void ImpressaoVaziaNuncaPassa() => Assert.False(CodigoAcesso.ImpressaoConfere("", ""));
}
