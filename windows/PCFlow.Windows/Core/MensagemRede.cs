namespace PCFlow.Windows.Core;

public sealed class MensagemRede
{
    public string Tipo { get; set; } = "";
    public string? Acao { get; set; }
    public string? DispositivoId { get; set; }
    public string? Nome { get; set; }
    public string? Token { get; set; }
    public string? Pin { get; set; }
    public double X { get; set; }
    public double Y { get; set; }
    public int Delta { get; set; }
    public string? Botao { get; set; }
    public string? Texto { get; set; }
    public string? Tecla { get; set; }
    public string? Mensagem { get; set; }
}
