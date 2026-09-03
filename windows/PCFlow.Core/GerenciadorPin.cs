using System.Net;
using System.Security.Cryptography;
using System.Text;

namespace PCFlow.Core;

/// <summary>
/// PIN de pareamento com validade e proteção contra força bruta.
/// A v1 mantinha o mesmo PIN indefinidamente e aceitava tentativas ilimitadas.
/// </summary>
public sealed class GerenciadorPin
{
    private readonly object _trava = new();
    private readonly Dictionary<string, (int Erros, DateTime Ate)> _bloqueios = new();
    private string _pin = "";
    private DateTime _expiraEm;

    public const int MaxTentativas = 5;
    public static readonly TimeSpan DuracaoBloqueio = TimeSpan.FromSeconds(60);

    public GerenciadorPin() => Renovar();

    public string Pin { get { lock (_trava) { GarantirValido(); return _pin; } } }

    public TimeSpan TempoRestante
    {
        get { lock (_trava) { GarantirValido(); return _expiraEm - DateTime.UtcNow; } }
    }

    public string Renovar()
    {
        lock (_trava)
        {
            _pin = RandomNumberGenerator.GetInt32(100000, 1000000).ToString("D6");
            _expiraEm = DateTime.UtcNow + Protocolo.ValidadePin;
            return _pin;
        }
    }

    private void GarantirValido()
    {
        if (DateTime.UtcNow >= _expiraEm)
        {
            _pin = RandomNumberGenerator.GetInt32(100000, 1000000).ToString("D6");
            _expiraEm = DateTime.UtcNow + Protocolo.ValidadePin;
        }
    }

    public bool EstaBloqueado(IPAddress origem, out TimeSpan restante)
    {
        lock (_trava)
        {
            restante = TimeSpan.Zero;
            var chave = origem.ToString();
            if (!_bloqueios.TryGetValue(chave, out var estado)) return false;
            if (estado.Erros < MaxTentativas) return false;
            if (DateTime.UtcNow >= estado.Ate) { _bloqueios.Remove(chave); return false; }
            restante = estado.Ate - DateTime.UtcNow;
            return true;
        }
    }

    /// <summary>Compara em tempo constante e contabiliza a tentativa por IP de origem.</summary>
    public bool Validar(string? informado, IPAddress origem)
    {
        lock (_trava)
        {
            GarantirValido();
            var limpo = (informado ?? "").Replace(" ", "").Replace("-", "");
            var esperado = Encoding.ASCII.GetBytes(_pin);
            var recebido = Encoding.ASCII.GetBytes(limpo);
            var ok = recebido.Length == esperado.Length
                     && CryptographicOperations.FixedTimeEquals(esperado, recebido);

            var chave = origem.ToString();
            if (ok)
            {
                _bloqueios.Remove(chave);
                return true;
            }

            var atual = _bloqueios.TryGetValue(chave, out var e) && DateTime.UtcNow < e.Ate ? e.Erros : 0;
            _bloqueios[chave] = (atual + 1, DateTime.UtcNow + DuracaoBloqueio);
            return false;
        }
    }

    public static string Formatar(string pin) => pin.Length == 6 ? $"{pin[..3]} {pin[3..]}" : pin;
}
