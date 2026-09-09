namespace PCFlow.Windows.Logica;

/// <summary>
/// Formatação dos números de identidade do PCFlow.
///
/// O ID e o código de pareamento são lidos em voz alta ao telefone o tempo
/// todo, então eles aparecem em grupos: "114 592 992" em vez de "114592992".
/// </summary>
public static class FormatoIdentificador
{
    /// <summary>Agrupa da esquerda para a direita, deixando o resto no fim.</summary>
    public static string EmGrupos(string? valor, int tamanhoGrupo = 3)
    {
        if (string.IsNullOrWhiteSpace(valor)) return "";
        var limpo = new string(valor.Where(c => !char.IsWhiteSpace(c)).ToArray());
        if (tamanhoGrupo <= 0 || limpo.Length <= tamanhoGrupo) return limpo;

        var partes = new List<string>();
        for (var i = 0; i < limpo.Length; i += tamanhoGrupo)
            partes.Add(limpo.Substring(i, Math.Min(tamanhoGrupo, limpo.Length - i)));
        return string.Join(' ', partes);
    }

    /// <summary>Tira o agrupamento de volta, para comparar ou enviar pela rede.</summary>
    public static string SemGrupos(string? valor)
        => string.IsNullOrEmpty(valor) ? "" : new string(valor.Where(char.IsLetterOrDigit).ToArray());

    /// <summary>Tamanho de arquivo em unidade que cabe na coluna da tabela.</summary>
    public static string Tamanho(long bytes)
    {
        if (bytes < 0) return "—";
        if (bytes < 1024) return $"{bytes} B";
        double valor = bytes;
        string[] unidades = ["KB", "MB", "GB", "TB"];
        foreach (var unidade in unidades)
        {
            valor /= 1024;
            if (valor < 1024)
                return valor < 10
                    ? $"{valor:0.0} {unidade}".Replace('.', ',')
                    : $"{valor:0} {unidade}";
        }
        return $"{valor:0} PB";
    }

    /// <summary>Velocidade da fila de transferência.</summary>
    public static string Velocidade(double bytesPorSegundo)
        => bytesPorSegundo <= 0 ? "—" : $"{Tamanho((long)bytesPorSegundo)}/s";

    /// <summary>Tempo restante em minutos e segundos, sem casas inúteis.</summary>
    public static string TempoRestante(TimeSpan tempo)
    {
        if (tempo <= TimeSpan.Zero || tempo > TimeSpan.FromDays(1)) return "—";
        return tempo.TotalHours >= 1
            ? $"{(int)tempo.TotalHours:00}:{tempo.Minutes:00}:{tempo.Seconds:00}"
            : $"{tempo.Minutes:00}:{tempo.Seconds:00}";
    }

    /// <summary>Data como o usuário lê: "Hoje, 21:42", "Ontem, 18:23", "12/05/2024 20:11".</summary>
    public static string QuandoRelativo(DateTime quando, DateTime? agora = null)
    {
        var referencia = agora ?? DateTime.Now;
        if (quando == default) return "Nunca";
        var dias = (referencia.Date - quando.Date).Days;
        return dias switch
        {
            0 => $"Hoje, {quando:HH:mm}",
            1 => $"Ontem, {quando:HH:mm}",
            > 1 and < 7 => $"{dias} dias atrás",
            _ => quando.ToString("dd/MM/yyyy HH:mm")
        };
    }
}
