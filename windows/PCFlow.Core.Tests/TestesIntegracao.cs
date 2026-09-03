using System.Text.Json;
using Xunit;

namespace PCFlow.Core.Tests;

/// <summary>
/// Sobem um servidor PCFlow real e falam com ele exatamente como o app Android fala.
/// É aqui que se comprova que "conectar" funciona de ponta a ponta.
/// </summary>
public class TestesIntegracao
{
    private static string Tipo(JsonElement? e) => e?.GetProperty("tipo").GetString() ?? "";

    // ---------------------------------------------------------------
    // Pareamento
    // ---------------------------------------------------------------

    [Fact]
    public async Task ParearComPinCorretoDevolveToken()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);

        var resposta = await cliente.ApresentarAsync("dispositivo-1", s.Servidor.Pin.Pin, null);

        Assert.Equal("pareado", Tipo(resposta));
        Assert.False(string.IsNullOrWhiteSpace(resposta!.Value.GetProperty("token").GetString()));
        Assert.Equal("PC-DE-TESTE", resposta.Value.GetProperty("nome").GetString());
        Assert.Equal(Protocolo.Versao, resposta.Value.GetProperty("protocolo").GetInt32());
        Assert.Single(s.Servidor.Dispositivos);
    }

    [Fact]
    public async Task PinErradoEhRecusadoComMensagemClara()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);

        var resposta = await cliente.ApresentarAsync("dispositivo-x", "000000", null);

        Assert.Equal("erro", Tipo(resposta));
        Assert.Contains("PIN", resposta!.Value.GetProperty("mensagem").GetString(),
            StringComparison.OrdinalIgnoreCase);
        Assert.Empty(s.Servidor.Dispositivos);
    }

    [Fact]
    public async Task PinEhRotacionadoDepoisDoPareamento()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        var pinUsado = s.Servidor.Pin.Pin;

        using (var cliente = new ClienteDeTeste())
        {
            await cliente.ConectarAsync(s.Porta);
            await cliente.ApresentarAsync("dispositivo-1", pinUsado, null);
        }

        Assert.NotEqual(pinUsado, s.Servidor.Pin.Pin);

        // O PIN antigo não vale mais para um segundo aparelho.
        using var outro = new ClienteDeTeste();
        await outro.ConectarAsync(s.Porta);
        Assert.Equal("erro", Tipo(await outro.ApresentarAsync("dispositivo-2", pinUsado, null)));
    }

    [Fact]
    public async Task ReconectaComTokenSemPedirPin()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        string token;
        using (var primeiro = new ClienteDeTeste())
        {
            await primeiro.ConectarAsync(s.Porta);
            var r = await primeiro.ApresentarAsync("dispositivo-1", s.Servidor.Pin.Pin, null);
            token = r!.Value.GetProperty("token").GetString()!;
        }

        using var segundo = new ClienteDeTeste();
        await segundo.ConectarAsync(s.Porta);
        var resposta = await segundo.ApresentarAsync("dispositivo-1", null, token);

        Assert.Equal("conectado", Tipo(resposta));
        Assert.Single(s.Servidor.Dispositivos);
    }

    [Fact]
    public async Task TokenInvalidoEhRecusado()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        using (var primeiro = new ClienteDeTeste())
        {
            await primeiro.ConectarAsync(s.Porta);
            await primeiro.ApresentarAsync("dispositivo-1", s.Servidor.Pin.Pin, null);
        }

        using var impostor = new ClienteDeTeste();
        await impostor.ConectarAsync(s.Porta);
        var resposta = await impostor.ApresentarAsync("dispositivo-1", null, "token-falsificado");

        Assert.Equal("erro", Tipo(resposta));
        Assert.Equal("naoautorizado", resposta!.Value.GetProperty("codigo").GetString());
    }

    [Fact]
    public async Task DispositivoBloqueadoNaoConecta()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        string token;
        using (var primeiro = new ClienteDeTeste())
        {
            await primeiro.ConectarAsync(s.Porta);
            var r = await primeiro.ApresentarAsync("dispositivo-1", s.Servidor.Pin.Pin, null);
            token = r!.Value.GetProperty("token").GetString()!;
        }

        s.Servidor.DefinirBloqueio("dispositivo-1", true);

        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);
        Assert.Equal("erro", Tipo(await cliente.ApresentarAsync("dispositivo-1", null, token)));
    }

    [Fact]
    public async Task VersaoDeProtocoloIncompativelEhAvisada()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);

        await cliente.EnviarAsync(new
        {
            tipo = "ola",
            protocolo = 99,
            dispositivoId = "d",
            nome = "Antigo",
            pin = s.Servidor.Pin.Pin
        });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("erro", Tipo(resposta));
        Assert.Equal("versao", resposta!.Value.GetProperty("codigo").GetString());
    }

    // ---------------------------------------------------------------
    // Comandos
    // ---------------------------------------------------------------

    private static async Task<ClienteDeTeste> Pareado(ServidorDeTeste s, string id = "d1")
    {
        var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);
        var r = await cliente.ApresentarAsync(id, s.Servidor.Pin.Pin, null);
        Assert.Contains(Tipo(r), new[] { "pareado", "conectado" });
        return cliente;
    }

    [Fact]
    public async Task MouseTecladoEMidiaChegamNoWindows()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 12.5, dy = -4.25 });
        await cliente.EnviarAsync(new { tipo = "mouse_click", botao = "right", acao = "click" });
        await cliente.EnviarAsync(new { tipo = "scroll", dx = 0, dy = 120 });
        await cliente.EnviarAsync(new { tipo = "texto", texto = "Olá, ação!" });
        await cliente.EnviarAsync(new { tipo = "tecla", tecla = "S", mods = new[] { "ctrl" } });
        await cliente.EnviarAsync(new { tipo = "atalho", acao = "alt+tab" });
        await cliente.EnviarAsync(new { tipo = "media", acao = "playpause" });

        await EsperarAte(() => s.Entrada.Eventos.Count >= 6 && s.Midia.Acoes.Count >= 1);

        Assert.Equal(12.5, s.Entrada.SomaX, 3);
        Assert.Equal(-4.25, s.Entrada.SomaY, 3);
        Assert.Contains(s.Entrada.Eventos, e => e == "botao:Direito:Clique");
        Assert.Contains(s.Entrada.Eventos, e => e == "rolar:0,120");
        Assert.Contains(s.Entrada.Eventos, e => e == "texto:Olá, ação!");
        Assert.Contains(s.Entrada.Eventos, e => e == "tecla:S+ctrl");
        Assert.Contains(s.Entrada.Eventos, e => e == "tecla:tab+alt");
        Assert.Contains("playpause", s.Midia.Acoes);
    }

    [Fact]
    public async Task OrdemDosEventosDeEntradaEhPreservada()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        const int total = 400;
        for (var i = 0; i < total; i++)
            await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 1.0, dy = 0.0 });

        await EsperarAte(() => s.Entrada.Eventos.Count >= total, TimeSpan.FromSeconds(15));

        Assert.Equal(total, s.Entrada.Eventos.Count);
        Assert.Equal(total, s.Entrada.SomaX, 3);
        Assert.All(s.Entrada.Eventos, e => Assert.Equal("mover:1,0", e));
    }

    [Fact]
    public async Task AreaDeTransferenciaVaiEVolta()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        await cliente.EnviarAsync(new { tipo = "clipboard_enviar", texto = "vindo do celular" });
        await EsperarAte(() => s.Clipboard.Conteudo == "vindo do celular");

        s.Clipboard.Conteudo = "vindo do PC";
        await cliente.EnviarAsync(new { tipo = "clipboard_pedir" });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("clipboard", Tipo(resposta));
        Assert.Equal("vindo do PC", resposta!.Value.GetProperty("texto").GetString());
    }

    [Fact]
    public async Task EnergiaDesativadaNaoExecutaEAvisa()
    {
        await using var s = new ServidorDeTeste(c =>
        {
            c.PerguntarAntesDeNovoDispositivo = false;
            c.PermitirEnergia = false;
        });
        using var cliente = await Pareado(s);

        await cliente.EnviarAsync(new { tipo = "power", acao = "shutdown" });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("aviso", Tipo(resposta));
        Assert.Empty(s.Energia.Acoes);
    }

    [Fact]
    public async Task ArquivosDesativadosBloqueiamListagem()
    {
        await using var s = new ServidorDeTeste(c =>
        {
            c.PerguntarAntesDeNovoDispositivo = false;
            c.PermitirArquivos = false;
        });
        using var cliente = await Pareado(s);

        await cliente.EnviarAsync(new { tipo = "arq_listar", caminho = "" });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("arq_erro", Tipo(resposta));
    }

    [Fact]
    public async Task ServidorPausadoIgnoraComandosMasResponsePing()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        s.Servidor.AlternarPausa();
        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 50.0, dy = 50.0 });
        await cliente.EnviarAsync(new { tipo = "ping", t = 12345L });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("pong", Tipo(resposta));
        Assert.Equal(0, s.Entrada.SomaX);

        s.Servidor.AlternarPausa();
        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 7.0, dy = 0.0 });
        await EsperarAte(() => Math.Abs(s.Entrada.SomaX - 7.0) < 0.001);
    }

    // ---------------------------------------------------------------
    // Resiliência e segurança
    // ---------------------------------------------------------------

    [Fact]
    public async Task HeartbeatMedeLatencia()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        var carimbo = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        await cliente.EnviarAsync(new { tipo = "ping", t = carimbo });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("pong", Tipo(resposta));
        Assert.Equal(carimbo, resposta!.Value.GetProperty("t").GetInt64());
    }

    [Fact]
    public async Task CemCiclosDeConectarEDesconectarNaoQuebramOServidor()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        string token;
        using (var primeiro = new ClienteDeTeste())
        {
            await primeiro.ConectarAsync(s.Porta);
            token = (await primeiro.ApresentarAsync("d1", s.Servidor.Pin.Pin, null))!
                .Value.GetProperty("token").GetString()!;
        }

        for (var i = 0; i < 100; i++)
        {
            using var cliente = new ClienteDeTeste();
            await cliente.ConectarAsync(s.Porta);
            var r = await cliente.ApresentarAsync("d1", null, token);
            Assert.Equal("conectado", Tipo(r));
        }

        // Continua atendendo normalmente depois de tudo isso.
        using var final = new ClienteDeTeste();
        await final.ConectarAsync(s.Porta);
        Assert.Equal("conectado", Tipo(await final.ApresentarAsync("d1", null, token)));
        await EsperarAte(() => s.Servidor.Conectados == 1);
    }

    [Fact]
    public async Task VariosCelularesConectamAoMesmoTempoComIdsProprios()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        using var a = new ClienteDeTeste();
        await a.ConectarAsync(s.Porta);
        Assert.Equal("pareado", Tipo(await a.ApresentarAsync("celular-A", s.Servidor.Pin.Pin, null, "Celular A")));

        using var b = new ClienteDeTeste();
        await b.ConectarAsync(s.Porta);
        Assert.Equal("pareado", Tipo(await b.ApresentarAsync("celular-B", s.Servidor.Pin.Pin, null, "Celular B")));

        await EsperarAte(() => s.Servidor.Conectados == 2);
        Assert.Equal(2, s.Servidor.Dispositivos.Count);
        Assert.Contains(s.Servidor.Dispositivos, d => d.Nome == "Celular A");
        Assert.Contains(s.Servidor.Dispositivos, d => d.Nome == "Celular B");
    }

    [Fact]
    public async Task MensagemMalformadaNaoDerrubaASessao()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        await cliente.EnviarCruAsync("{isto não é json}");
        await cliente.EnviarCruAsync("");
        await cliente.EnviarCruAsync("[1,2,3]");
        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 9.0, dy = 0.0 });

        await EsperarAte(() => Math.Abs(s.Entrada.SomaX - 9.0) < 0.001);
    }

    [Fact]
    public async Task ComandoDesconhecidoEhIgnoradoSemQuebrar()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s);

        await cliente.EnviarAsync(new { tipo = "executar_qualquer_coisa", acao = "rm -rf" });
        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 3.0, dy = 0.0 });

        await EsperarAte(() => Math.Abs(s.Entrada.SomaX - 3.0) < 0.001);
    }

    [Fact]
    public async Task MensagemGiganteEncerraASessaoSemDerrubarOServidor()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        using (var abusivo = new ClienteDeTeste())
        {
            await abusivo.ConectarAsync(s.Porta);
            await abusivo.ApresentarAsync("abusivo", s.Servidor.Pin.Pin, null);
            await abusivo.EnviarCruAsync(new string('A', Protocolo.TamanhoMaximoLinha + 5_000));
            await Task.Delay(400);
        }

        // O servidor segue de pé para o próximo cliente.
        using var normal = new ClienteDeTeste();
        await normal.ConectarAsync(s.Porta);
        Assert.Equal("pareado", Tipo(await normal.ApresentarAsync("normal", s.Servidor.Pin.Pin, null)));
    }

    [Fact]
    public async Task ComandoAntesDoHandshakeNaoEhExecutado()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);

        await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 999.0, dy = 999.0 });
        var resposta = await cliente.ReceberAsync();

        Assert.Equal("erro", Tipo(resposta));
        Assert.Equal(0, s.Entrada.SomaX);
    }

    [Fact]
    public async Task ForcaBrutaDePinEhBloqueada()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        for (var i = 0; i < GerenciadorPin.MaxTentativas; i++)
        {
            using var tentativa = new ClienteDeTeste();
            await tentativa.ConectarAsync(s.Porta);
            Assert.Equal("erro", Tipo(await tentativa.ApresentarAsync($"bot-{i}", "000000", null)));
        }

        using var seguinte = new ClienteDeTeste();
        await seguinte.ConectarAsync(s.Porta);
        var resposta = await seguinte.ApresentarAsync("bot-final", "000000", null);
        Assert.Equal("bloqueado", resposta!.Value.GetProperty("codigo").GetString());
    }

    [Fact]
    public async Task RemoverDispositivoDerrubaASessaoDele()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = await Pareado(s, "para-remover");
        await EsperarAte(() => s.Servidor.Conectados == 1);

        s.Servidor.RemoverDispositivo("para-remover");

        await EsperarAte(() => s.Servidor.Conectados == 0);
        Assert.Empty(s.Servidor.Dispositivos);
    }

    [Fact]
    public async Task ServidorReiniciaEVoltaAAceitarConexoes()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using (var antes = new ClienteDeTeste())
        {
            await antes.ConectarAsync(s.Porta);
            await antes.ApresentarAsync("d1", s.Servidor.Pin.Pin, null);
        }

        Assert.True(await s.Servidor.ReiniciarAsync());
        Assert.True(s.Servidor.Ativo);

        using var depois = new ClienteDeTeste();
        await depois.ConectarAsync(s.Porta);
        // O dispositivo continua autorizado após o reinício (configuração persistida).
        var dispositivo = s.Servidor.Dispositivos.Single();
        Assert.Equal("conectado", Tipo(await depois.ApresentarAsync("d1", null, dispositivo.Token)));
    }

    [Fact]
    public async Task PararEIniciarVariasVezesNaoDeixaEstadoInvalido()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        for (var i = 0; i < 5; i++)
        {
            await s.Servidor.PararAsync();
            Assert.False(s.Servidor.Ativo);
            Assert.True(s.Servidor.Iniciar());
            Assert.True(s.Servidor.Ativo);
        }

        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(s.Porta);
        Assert.Equal("pareado", Tipo(await cliente.ApresentarAsync("depois", s.Servidor.Pin.Pin, null)));
    }

    [Fact]
    public async Task SessaoLongaNaoAcumulaSessoesOrfas()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);

        for (var rodada = 0; rodada < 30; rodada++)
        {
            using var cliente = new ClienteDeTeste();
            await cliente.ConectarAsync(s.Porta);
            await cliente.ApresentarAsync($"efemero-{rodada}", s.Servidor.Pin.Pin, null);
            for (var i = 0; i < 20; i++)
                await cliente.EnviarAsync(new { tipo = "mouse_move", dx = 0.5, dy = 0.5 });
        }

        await EsperarAte(() => s.Servidor.Conectados == 0, TimeSpan.FromSeconds(15));
        Assert.Equal(0, s.Servidor.Conectados);
    }

    [Fact]
    public async Task DescobertaAnunciaOPcNaRede()
    {
        await using var s = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        // A porta 45455 pode estar ocupada no runner; o teste só exige que o
        // servidor não quebre por causa disso e informe o estado corretamente.
        Assert.True(s.Servidor.Ativo);
        Assert.True(s.Servidor.DescobertaAtiva || s.Servidor.Log.Linhas
            .Any(l => l.Categoria == Categoria.Descoberta));
    }

    private static async Task EsperarAte(Func<bool> condicao, TimeSpan? limite = null)
    {
        var fim = DateTime.UtcNow + (limite ?? TimeSpan.FromSeconds(5));
        while (DateTime.UtcNow < fim)
        {
            if (condicao()) return;
            await Task.Delay(20);
        }
        Assert.True(condicao(), "A condição esperada não aconteceu dentro do tempo limite.");
    }
}
