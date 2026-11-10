using System.Security.Cryptography;
using System.Text;

namespace PCFlow.Core.Trust;

public enum TrustDecision
{
    Unknown,
    Ok,
    Rejected,
    Revoked
}

public sealed class TrustStore
{
    private readonly List<DispositivoAutorizado> _devices;

    public TrustStore(List<DispositivoAutorizado> devices) => _devices = devices;

    public IReadOnlyList<DispositivoAutorizado> Devices => _devices;

    public TrustDecision Authorize(string deviceId, string? token)
    {
        var found = Find(deviceId);
        if (found is null)
            return TrustDecision.Unknown;
        if (found.Revogado)
            return TrustDecision.Revoked;
        var given = Encoding.UTF8.GetBytes(token ?? "");
        var expected = Encoding.UTF8.GetBytes(found.Token);
        if (given.Length != expected.Length)
        {
            CryptographicOperations.FixedTimeEquals(expected, expected);
            return TrustDecision.Rejected;
        }
        return CryptographicOperations.FixedTimeEquals(given, expected) ? TrustDecision.Ok : TrustDecision.Rejected;
    }

    public bool TryPair(string deviceId, string name, string pin, string expectedPin, out string token)
    {
        token = "";
        if (!PinEquals(pin, expectedPin))
            return false;
        token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
        var found = Find(deviceId);
        if (found is null)
        {
            _devices.Add(new DispositivoAutorizado
            {
                Id = deviceId,
                Nome = string.IsNullOrWhiteSpace(name) ? "Android" : name,
                Token = token,
                Revogado = false,
                PareadoEm = DateTime.UtcNow,
                UltimaConexao = DateTime.UtcNow
            });
        }
        else
        {
            found.Nome = string.IsNullOrWhiteSpace(name) ? found.Nome : name;
            found.Token = token;
            found.Revogado = false;
            found.UltimaConexao = DateTime.UtcNow;
        }
        return true;
    }

    public void Touch(string deviceId)
    {
        var found = Find(deviceId);
        if (found is not null)
            found.UltimaConexao = DateTime.UtcNow;
    }

    public bool Revoke(string deviceId)
    {
        var found = Find(deviceId);
        if (found is null)
            return false;
        found.Revogado = true;
        found.Token = "";
        return true;
    }

    public static bool PinEquals(string provided, string expected)
    {
        var a = Encoding.UTF8.GetBytes((provided ?? "").Trim());
        var b = Encoding.UTF8.GetBytes((expected ?? "").Trim());
        if (a.Length != b.Length)
        {
            CryptographicOperations.FixedTimeEquals(b, b);
            return false;
        }
        return CryptographicOperations.FixedTimeEquals(a, b);
    }

    private DispositivoAutorizado? Find(string deviceId) =>
        _devices.FirstOrDefault(d => string.Equals(d.Id, deviceId, StringComparison.Ordinal));
}
