namespace PCFlow.Windows.Logica;

/// <summary>
/// Medidor de força da senha de acesso não supervisionado.
///
/// Não é entropia de verdade nem pretende ser: é um sinal honesto para o
/// usuário, calibrado no que o servidor exige (mínimo de 8) e no que costuma
/// derrubar uma senha na prática — repetição, sequência e alfabeto pequeno.
/// </summary>
public static class ForcaSenha
{
    public const int TamanhoMinimo = 8;

    public static AvaliacaoSenha Avaliar(string? senha)
    {
        senha ??= "";
        if (senha.Length == 0)
            return new AvaliacaoSenha(0, "Sem senha", "Digite uma senha de pelo menos 8 caracteres.", false);

        if (senha.Length < TamanhoMinimo)
            return new AvaliacaoSenha(1, "Curta demais",
                $"Faltam {TamanhoMinimo - senha.Length} caractere(s) para o mínimo aceito.", false);

        var pontos = 0;
        if (senha.Length >= 8) pontos++;
        if (senha.Length >= 12) pontos++;
        if (senha.Length >= 16) pontos++;

        var temMinuscula = senha.Any(char.IsLower);
        var temMaiuscula = senha.Any(char.IsUpper);
        var temNumero = senha.Any(char.IsDigit);
        var temSimbolo = senha.Any(c => !char.IsLetterOrDigit(c));
        var variedade = (temMinuscula ? 1 : 0) + (temMaiuscula ? 1 : 0) + (temNumero ? 1 : 0) + (temSimbolo ? 1 : 0);
        pontos += variedade >= 3 ? 2 : variedade == 2 ? 1 : 0;

        // Um alfabeto minúsculo estica a senha sem melhorar nada.
        if (senha.Distinct().Count() <= 3) pontos -= 2;
        if (TemSequencia(senha)) pontos--;
        if (TemRepeticao(senha)) pontos--;

        pontos = Math.Clamp(pontos, 1, 4);
        return pontos switch
        {
            1 => new AvaliacaoSenha(1, "Senha fraca", "Misture letras maiúsculas, números e símbolos.", true),
            2 => new AvaliacaoSenha(2, "Senha razoável", "Aumente o tamanho para 12 caracteres ou mais.", true),
            3 => new AvaliacaoSenha(3, "Senha boa", "Serve bem para acesso não assistido.", true),
            _ => new AvaliacaoSenha(4, "Senha forte", "Ótima para deixar o computador acessível sem você.", true)
        };
    }

    /// <summary>Três ou mais caracteres em sequência crescente ou decrescente.</summary>
    private static bool TemSequencia(string senha)
    {
        for (var i = 0; i + 2 < senha.Length; i++)
        {
            var a = senha[i];
            var b = senha[i + 1];
            var c = senha[i + 2];
            if (b - a == 1 && c - b == 1) return true;
            if (a - b == 1 && b - c == 1) return true;
        }
        return false;
    }

    private static bool TemRepeticao(string senha)
    {
        for (var i = 0; i + 2 < senha.Length; i++)
            if (senha[i] == senha[i + 1] && senha[i + 1] == senha[i + 2]) return true;
        return false;
    }
}

/// <param name="Pontos">1 a 4; 0 quando não há senha.</param>
/// <param name="Rotulo">Texto curto ao lado da barra.</param>
/// <param name="Dica">O que fazer para melhorar.</param>
/// <param name="Aceitavel">Se o servidor aceitaria esta senha.</param>
public sealed record AvaliacaoSenha(int Pontos, string Rotulo, string Dica, bool Aceitavel)
{
    /// <summary>Quanto da barra preencher, de 0 a 1.</summary>
    public double Fracao => Pontos / 4.0;
}
