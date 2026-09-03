using System.Security.Cryptography;

namespace PCFlow.Windows.Core;

public static class SegurancaSenha
{
    private const int Iteracoes = 210_000;

    public static (string salt, string hash) Criar(string senha)
    {
        var salt = RandomNumberGenerator.GetBytes(24);
        var hash = Rfc2898DeriveBytes.Pbkdf2(senha, salt, Iteracoes, HashAlgorithmName.SHA256, 32);
        return (Convert.ToBase64String(salt), Convert.ToBase64String(hash));
    }

    public static bool Verificar(string senha, string? saltBase64, string? hashBase64)
    {
        if (string.IsNullOrWhiteSpace(senha) || string.IsNullOrWhiteSpace(saltBase64) || string.IsNullOrWhiteSpace(hashBase64)) return false;
        try
        {
            var salt = Convert.FromBase64String(saltBase64);
            var esperado = Convert.FromBase64String(hashBase64);
            var atual = Rfc2898DeriveBytes.Pbkdf2(senha, salt, Iteracoes, HashAlgorithmName.SHA256, esperado.Length);
            return CryptographicOperations.FixedTimeEquals(atual, esperado);
        }
        catch { return false; }
    }
}
