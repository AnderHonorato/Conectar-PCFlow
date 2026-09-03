using System.Net;
using Xunit;

namespace PCFlow.Core.Tests;

public class TestesTeclas
{
    [Theory]
    [InlineData("ENTER", 0x0D)]
    [InlineData("enter", 0x0D)]
    [InlineData("ESC", 0x1B)]
    [InlineData("F5", 0x74)]
    [InlineData("a", 'A')]
    [InlineData("Z", 'Z')]
    [InlineData("7", '7')]
    [InlineData("PAGEDOWN", 0x22)]
    public void ResolveTeclasConhecidas(string nome, int esperado)
        => Assert.Equal((ushort)esperado, Teclas.Resolver(nome));

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("tecla-que-nao-existe")]
    [InlineData(null)]
    public void RecusaTeclasDesconhecidas(string? nome) => Assert.Null(Teclas.Resolver(nome));

    [Fact]
    public void InterpretaComboComVariosModificadores()
    {
        var (mods, tecla) = Teclas.InterpretarCombo("ctrl+shift+s");
        Assert.Equal(2, mods.Count);
        Assert.Contains((ushort)0x11, mods);
        Assert.Contains((ushort)0x10, mods);
        Assert.Equal((ushort)'S', tecla);
    }

    [Fact]
    public void InterpretaComboComEspacos()
    {
        var (mods, tecla) = Teclas.InterpretarCombo(" alt + tab ");
        Assert.Single(mods);
        Assert.Equal((ushort)0x09, tecla);
    }

    [Theory]
    [InlineData("playpause", 0xB3)]
    [InlineData("next", 0xB0)]
    [InlineData("mudo", 0xAD)]
    [InlineData("volumeup", 0xAF)]
    public void ResolveAcoesDeMidia(string acao, int vk)
        => Assert.Equal((ushort)vk, Teclas.ResolverMidia(acao));

    [Fact]
    public void AcaoDeMidiaDesconhecidaNaoResolve() => Assert.Null(Teclas.ResolverMidia("teletransporte"));
}

public class TestesPin
{
    [Fact]
    public void PinTemSeisDigitos()
    {
        var gerenciador = new GerenciadorPin();
        Assert.Matches("^[0-9]{6}$", gerenciador.Pin);
    }

    [Fact]
    public void RenovarTrocaOPin()
    {
        var gerenciador = new GerenciadorPin();
        var antigo = gerenciador.Pin;
        // Improvável, mas o PIN pode repetir: tenta algumas vezes.
        var mudou = Enumerable.Range(0, 10).Any(_ => gerenciador.Renovar() != antigo);
        Assert.True(mudou);
    }

    [Fact]
    public void ValidaPinCorretoEIgnoraEspacos()
    {
        var gerenciador = new GerenciadorPin();
        var pin = gerenciador.Pin;
        var formatado = $"{pin[..3]} {pin[3..]}";
        Assert.True(gerenciador.Validar(formatado, IPAddress.Parse("192.168.0.5")));
    }

    [Fact]
    public void BloqueiaForcaBrutaDepoisDeCincoErros()
    {
        var gerenciador = new GerenciadorPin();
        var origem = IPAddress.Parse("192.168.0.99");

        for (var i = 0; i < GerenciadorPin.MaxTentativas; i++)
            Assert.False(gerenciador.Validar("000000", origem));

        Assert.True(gerenciador.EstaBloqueado(origem, out var restante));
        Assert.True(restante > TimeSpan.Zero);
    }

    [Fact]
    public void BloqueioEhPorOrigemENaoGlobal()
    {
        var gerenciador = new GerenciadorPin();
        var atacante = IPAddress.Parse("192.168.0.99");
        var inocente = IPAddress.Parse("192.168.0.44");

        for (var i = 0; i < GerenciadorPin.MaxTentativas; i++) gerenciador.Validar("000000", atacante);

        Assert.True(gerenciador.EstaBloqueado(atacante, out _));
        Assert.False(gerenciador.EstaBloqueado(inocente, out _));
    }

    [Fact]
    public void PinVazioNaoPassa()
    {
        var gerenciador = new GerenciadorPin();
        Assert.False(gerenciador.Validar("", IPAddress.Loopback));
        Assert.False(gerenciador.Validar(null, IPAddress.Loopback));
    }
}

public class TestesRede
{
    [Theory]
    [InlineData("192.168.0.10", true)]
    [InlineData("10.1.2.3", true)]
    [InlineData("172.16.0.1", true)]
    [InlineData("172.31.255.254", true)]
    [InlineData("127.0.0.1", true)]
    [InlineData("8.8.8.8", false)]
    [InlineData("172.32.0.1", false)]
    [InlineData("203.0.113.7", false)]
    public void ClassificaRedePrivada(string ip, bool privada)
        => Assert.Equal(privada, RedeUtil.EhRedePrivada(IPAddress.Parse(ip)));

    [Fact]
    public void EnderecoLocalSempreDevolveAlgoUtil()
        => Assert.False(string.IsNullOrWhiteSpace(RedeUtil.EnderecoLocal()));
}

public class TestesConfiguracao
{
    [Fact]
    public void SalvaERecarregaConfiguracao()
    {
        var armazenamento = Fabrica.ArmazenamentoTemporario(out var pasta);
        try
        {
            var cfg = new ConfiguracaoPcFlow
            {
                AoFechar = AcaoAoFechar.Encerrar,
                PortaControle = 45999,
                SomenteRedeLocal = false
            };
            cfg.Dispositivos.Add(new DispositivoAutorizado { Id = "abc", Nome = "Galaxy", Token = "t" });
            armazenamento.Salvar(cfg);

            var lida = armazenamento.Carregar();
            Assert.Equal(AcaoAoFechar.Encerrar, lida.AoFechar);
            Assert.Equal(45999, lida.PortaControle);
            Assert.False(lida.SomenteRedeLocal);
            Assert.Single(lida.Dispositivos);
            Assert.Equal("Galaxy", lida.Dispositivos[0].Nome);
        }
        finally { Directory.Delete(pasta, true); }
    }

    [Fact]
    public void ConfiguracaoCorrompidaNaoDerrubaOApp()
    {
        var armazenamento = Fabrica.ArmazenamentoTemporario(out var pasta);
        try
        {
            File.WriteAllText(armazenamento.Caminho, "{ isto não é json válido");
            var lida = armazenamento.Carregar();
            Assert.NotNull(lida);
            Assert.Equal(AcaoAoFechar.MinimizarParaBandeja, lida.AoFechar);
        }
        finally { Directory.Delete(pasta, true); }
    }

    [Fact]
    public void GravacoesSimultaneasNaoCorrompemOArquivo()
    {
        var armazenamento = Fabrica.ArmazenamentoTemporario(out var pasta);
        try
        {
            var cfg = new ConfiguracaoPcFlow();
            Parallel.For(0, 60, i =>
            {
                cfg.PortaControle = 45000 + (i % 10);
                armazenamento.Salvar(cfg);
            });
            var lida = armazenamento.Carregar();
            Assert.InRange(lida.PortaControle, 45000, 45009);
        }
        finally { Directory.Delete(pasta, true); }
    }
}

public class TestesArquivos
{
    private static ServicoArquivos Criar(string raiz)
    {
        var cfg = new ConfiguracaoPcFlow { PastasCompartilhadas = [raiz] };
        return new ServicoArquivos(() => cfg);
    }

    [Fact]
    public void ListaArquivosDaPastaAutorizada()
    {
        var raiz = Path.Combine(Path.GetTempPath(), "pcflow-arq-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(raiz);
        try
        {
            File.WriteAllText(Path.Combine(raiz, "nota.txt"), "oi");
            Directory.CreateDirectory(Path.Combine(raiz, "sub"));

            var itens = Criar(raiz).Listar(raiz);
            Assert.Contains(itens, i => i.Nome == "nota.txt" && !i.Pasta);
            Assert.Contains(itens, i => i.Nome == "sub" && i.Pasta);
            // Pastas vêm primeiro.
            Assert.True(itens[0].Pasta);
        }
        finally { Directory.Delete(raiz, true); }
    }

    [Fact]
    public void RecusaPathTraversal()
    {
        var raiz = Path.Combine(Path.GetTempPath(), "pcflow-arq-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(raiz);
        try
        {
            var servico = Criar(raiz);
            Assert.False(servico.CaminhoPermitido(Path.Combine(raiz, "..", "..", "etc", "passwd"), out _));
            Assert.False(servico.CaminhoPermitido("/etc/passwd", out _));
            Assert.False(servico.CaminhoPermitido(@"C:\Windows\System32\config\SAM", out _));
            Assert.True(servico.CaminhoPermitido(Path.Combine(raiz, "qualquer.txt"), out _));
        }
        finally { Directory.Delete(raiz, true); }
    }

    [Fact]
    public void LeArquivoEmBlocosSequenciais()
    {
        var raiz = Path.Combine(Path.GetTempPath(), "pcflow-arq-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(raiz);
        try
        {
            var caminho = Path.Combine(raiz, "grande.bin");
            var conteudo = new byte[ServicoArquivos.TamanhoBloco * 2 + 777];
            Random.Shared.NextBytes(conteudo);
            File.WriteAllBytes(caminho, conteudo);

            var servico = Criar(raiz);
            var recomposto = new List<byte>();
            long offset = 0;
            while (true)
            {
                var bloco = servico.LerBloco(caminho, offset, out var total);
                Assert.Equal(conteudo.Length, total);
                if (bloco is null) break;
                recomposto.AddRange(bloco);
                offset += bloco.Length;
            }
            Assert.Equal(conteudo, recomposto.ToArray());
        }
        finally { Directory.Delete(raiz, true); }
    }

    [Theory]
    [InlineData(@"..\..\Windows\perigo.txt", "perigo.txt")]
    [InlineData("../../etc/passwd", "passwd")]
    [InlineData(@"C:\Windows\System32\drivers\etc\hosts", "hosts")]
    [InlineData("....//....//alvo.bin", "alvo.bin")]
    [InlineData("normal.pdf", "normal.pdf")]
    public void NomeVindoDaRedeEhReduzidoAoArquivo(string entrada, string esperado)
        => Assert.Equal(esperado, ServicoArquivos.NomeSeguro(entrada));

    [Fact]
    public void ArquivoRecebidoSempreCaiNaPastaDeDownloads()
    {
        var cfg = new ConfiguracaoPcFlow();
        var servico = new ServicoArquivos(() => cfg);
        var destino = servico.GravarBloco(@"..\..\Windows\perigo.txt", 0, [1, 2, 3]);
        try
        {
            Assert.Equal(servico.PastaRecebidos(), Path.GetDirectoryName(destino));
            Assert.EndsWith("perigo.txt", destino);
        }
        finally { File.Delete(destino); }
    }
}

public class TestesRegistro
{
    [Fact]
    public void GuardaNoMaximoQuinhentasLinhas()
    {
        var log = new Registro();
        for (var i = 0; i < 700; i++) log.Escrever(Categoria.Comando, $"linha {i}");
        Assert.True(log.Linhas.Count <= 500);
        Assert.Contains(log.Linhas, l => l.Texto == "linha 699");
    }
}
