using System.Text.Json;
using Xunit;

namespace PCFlow.Core.Tests;

/// <summary>
/// Interoperabilidade real entre os dois projetos: o servidor Windows precisa
/// aceitar exatamente o handshake que o app Android produz.
///
/// O arquivo tests/interop/handshake-android.json é gerado e conferido por um
/// teste do lado Kotlin. Se algum dos lados mudar o formato sem avisar o outro,
/// um destes testes quebra.
/// </summary>
public class TestesInterop
{
    private static string CaminhoDoArquivo()
    {
        var pasta = AppContext.BaseDirectory;
        for (var i = 0; i < 8 && pasta is not null; i++)
        {
            var alvo = Path.Combine(pasta, "tests", "interop", "handshake-android.json");
            if (File.Exists(alvo)) return alvo;
            pasta = Directory.GetParent(pasta)?.FullName;
        }
        throw new FileNotFoundException(
            "tests/interop/handshake-android.json não encontrado a partir de " + AppContext.BaseDirectory);
    }

    [Fact]
    public void ArquivoDeInteroperabilidadeEstaNoFormatoEsperado()
    {
        var json = JsonDocument.Parse(File.ReadAllText(CaminhoDoArquivo())).RootElement;
        Assert.Equal("ola", json.GetProperty("tipo").GetString());
        Assert.Equal(Protocolo.Versao, json.GetProperty("protocolo").GetInt32());
        Assert.True(json.TryGetProperty("dispositivoId", out _));
        Assert.True(json.TryGetProperty("pin", out _));
    }

    [Fact]
    public async Task HandshakeGeradoPeloAndroidEhAceito()
    {
        var linha = File.ReadAllText(CaminhoDoArquivo()).Trim();

        await using var servidor = new ServidorDeTeste(c => c.PerguntarAntesDeNovoDispositivo = false);
        using var cliente = new ClienteDeTeste();
        await cliente.ConectarAsync(servidor.Porta);

        // O PIN do arquivo é fixo; o servidor precisa aceitar o formato mesmo
        // recusando o valor, então o teste ajusta apenas o campo "pin".
        var json = JsonNode();
        json["pin"] = servidor.Servidor.Pin.Pin;
        await cliente.EnviarCruAsync(JsonSerializer.Serialize(json, Protocolo.Json));

        var resposta = await cliente.ReceberAsync();
        Assert.NotNull(resposta);
        Assert.Equal("pareado", resposta!.Value.GetProperty("tipo").GetString());
        Assert.Equal("Galaxy de Anderson", servidor.Servidor.Dispositivos.Single().Nome);
        Assert.Equal("Samsung SM-G991B", servidor.Servidor.Dispositivos.Single().Modelo);

        Dictionary<string, object?> JsonNode()
        {
            var doc = JsonDocument.Parse(linha).RootElement;
            var mapa = new Dictionary<string, object?>();
            foreach (var propriedade in doc.EnumerateObject())
            {
                mapa[propriedade.Name] = propriedade.Value.ValueKind switch
                {
                    JsonValueKind.Number => propriedade.Value.GetInt32(),
                    _ => propriedade.Value.GetString()
                };
            }
            return mapa;
        }
    }
}
