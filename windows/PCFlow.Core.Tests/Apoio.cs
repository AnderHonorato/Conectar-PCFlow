using System.Collections.Concurrent;
using PCFlow.Core;

namespace PCFlow.Core.Tests;

/// <summary>Grava tudo que o servidor mandaria para o Windows, para conferência nos testes.</summary>
public sealed class EntradaGravada : IControleEntrada
{
    public ConcurrentQueue<string> Eventos { get; } = new();
    public double SomaX;
    public double SomaY;

    public void MoverRelativo(double dx, double dy)
    {
        SomaX += dx;
        SomaY += dy;
        Eventos.Enqueue($"mover:{dx:0.##},{dy:0.##}");
    }

    public void MoverAbsoluto(double x, double y) => Eventos.Enqueue($"abs:{x:0.###},{y:0.###}");

    public void Botao(BotaoMouse botao, AcaoBotao acao) => Eventos.Enqueue($"botao:{botao}:{acao}");

    public void Rolar(int dx, int dy) => Eventos.Enqueue($"rolar:{dx},{dy}");

    public void DigitarTexto(string texto) => Eventos.Enqueue($"texto:{texto}");

    public void PressionarTecla(string tecla, IReadOnlyList<string>? modificadores)
        => Eventos.Enqueue($"tecla:{tecla}" +
            (modificadores is { Count: > 0 } ? "+" + string.Join(",", modificadores) : ""));

    public bool Contem(string prefixo) => Eventos.Any(e => e.StartsWith(prefixo, StringComparison.Ordinal));
}

public sealed class MidiaGravada : IControleMidia
{
    public List<string> Acoes { get; } = [];
    public void Executar(string acao) => Acoes.Add(acao);
}

public sealed class EnergiaGravada : IControleEnergia
{
    public List<string> Acoes { get; } = [];
    public bool Executar(string acao)
    {
        Acoes.Add(acao);
        return acao is "lock" or "sleep" or "shutdown" or "restart" or "hibernate" or "monitoroff";
    }
}

public sealed class AreaTransferenciaFake : IAreaTransferencia
{
    public string Conteudo = "";
    public string? Ler() => Conteudo;
    public void Escrever(string texto) => Conteudo = texto;
}

public static class Fabrica
{
    public static (ServicosPlataforma Plataforma, EntradaGravada Entrada,
        MidiaGravada Midia, EnergiaGravada Energia, AreaTransferenciaFake Clip) Criar()
    {
        var entrada = new EntradaGravada();
        var midia = new MidiaGravada();
        var energia = new EnergiaGravada();
        var clip = new AreaTransferenciaFake();
        return (new ServicosPlataforma
        {
            Entrada = entrada,
            Midia = midia,
            Energia = energia,
            AreaTransferencia = clip,
            NomeMaquina = "PC-DE-TESTE"
        }, entrada, midia, energia, clip);
    }

    /// <summary>Pasta temporária isolada para a configuração de cada teste.</summary>
    public static ArmazenamentoConfiguracao ArmazenamentoTemporario(out string pasta)
    {
        pasta = Path.Combine(Path.GetTempPath(), "pcflow-testes", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(pasta);
        return new ArmazenamentoConfiguracao(pasta);
    }
}
