namespace PCFlow.Core;

public enum BotaoMouse { Esquerdo, Direito, Meio }
public enum AcaoBotao { Clique, Pressionar, Soltar }

/// <summary>
/// Injeção de entrada no sistema operacional. Abstraído para que o núcleo
/// seja testável fora do Windows (os testes usam uma implementação gravadora).
/// </summary>
public interface IControleEntrada
{
    void MoverRelativo(double dx, double dy);
    void MoverAbsoluto(double xNormalizado, double yNormalizado);
    void Botao(BotaoMouse botao, AcaoBotao acao);
    void Rolar(int dx, int dy);
    void DigitarTexto(string texto);
    /// <summary>Pressiona uma tecla nomeada (ver <see cref="Teclas"/>) com modificadores opcionais.</summary>
    void PressionarTecla(string tecla, IReadOnlyList<string>? modificadores);
}

public interface IControleMidia
{
    void Executar(string acao);
}

public interface IControleEnergia
{
    /// <summary>Retorna false quando a ação é desconhecida ou foi bloqueada pela configuração.</summary>
    bool Executar(string acao);
}

public interface IAreaTransferencia
{
    string? Ler();
    void Escrever(string texto);
}

public sealed record QuadroTela(byte[] Jpeg, int Largura, int Altura);

public interface ICapturaTela
{
    /// <summary>Captura o monitor principal já redimensionado e comprimido em JPEG.</summary>
    QuadroTela? Capturar(int larguraMaxima, int qualidade);
}

/// <summary>Atalho/aplicativo que o celular pode disparar no PC.</summary>
public sealed record AtalhoPc(string Id, string Nome, string Alvo, string? Argumentos = null);

public interface ILancadorAplicativos
{
    IReadOnlyList<AtalhoPc> Listar();
    bool Executar(string id);
}

/// <summary>Conjunto de serviços da plataforma consumidos pelo servidor.</summary>
public sealed class ServicosPlataforma
{
    public required IControleEntrada Entrada { get; init; }
    public required IControleMidia Midia { get; init; }
    public required IControleEnergia Energia { get; init; }
    public IAreaTransferencia? AreaTransferencia { get; init; }
    public ICapturaTela? CapturaTela { get; init; }
    public ILancadorAplicativos? Lancador { get; init; }
    public string NomeMaquina { get; init; } = Environment.MachineName;
}
