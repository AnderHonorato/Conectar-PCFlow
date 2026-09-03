using System.Collections.Concurrent;

namespace PCFlow.Core;

public enum Categoria { Conexao, Descoberta, Autenticacao, Comando, Arquivo, Tela, Erro }

public sealed record LinhaLog(DateTime Quando, Categoria Categoria, string Texto)
{
    public override string ToString() => $"{Quando:HH:mm:ss} [{Categoria}] {Texto}";
}

/// <summary>
/// Log local em memória (últimas 500 linhas) com exportação para arquivo.
/// Nunca registra PIN, token, texto digitado ou conteúdo de área de transferência.
/// </summary>
public sealed class Registro
{
    private readonly ConcurrentQueue<LinhaLog> _linhas = new();
    private const int Maximo = 500;

    public event Action<LinhaLog>? Adicionado;

    public void Escrever(Categoria categoria, string texto)
    {
        var linha = new LinhaLog(DateTime.Now, categoria, texto);
        _linhas.Enqueue(linha);
        while (_linhas.Count > Maximo) _linhas.TryDequeue(out _);
        Adicionado?.Invoke(linha);
    }

    public IReadOnlyList<LinhaLog> Linhas => _linhas.ToArray();

    public string Exportar(string pasta)
    {
        Directory.CreateDirectory(pasta);
        var arquivo = Path.Combine(pasta, $"pcflow-diagnostico-{DateTime.Now:yyyyMMdd-HHmmss}.txt");
        var conteudo = new List<string>
        {
            $"PCFlow {Protocolo.VersaoApp} — protocolo v{Protocolo.Versao}",
            $"Máquina: {Environment.MachineName}   SO: {Environment.OSVersion}",
            $"Endereço local: {RedeUtil.EnderecoLocal()}",
            $"Gerado em: {DateTime.Now:dd/MM/yyyy HH:mm:ss}",
            new string('-', 60)
        };
        conteudo.AddRange(Linhas.Select(l => l.ToString()));
        File.WriteAllLines(arquivo, conteudo);
        return arquivo;
    }
}
