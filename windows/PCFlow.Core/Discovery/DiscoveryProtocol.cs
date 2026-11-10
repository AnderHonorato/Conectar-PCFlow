using System.Text.Json;

namespace PCFlow.Core.Discovery;

public sealed record PcAnnouncement(string DeviceId, string Name, string Host, int Port, long SeenAtMs);

public static class DiscoveryProtocol
{
    public const string Request = "PCFLOW_DISCOVER_V1";
    public const int Protocol = 2;

    public static bool IsRequest(string text) =>
        text.StartsWith(Request, StringComparison.Ordinal);

    public static string Response(string name, int port, string deviceId)
    {
        return JsonSerializer.Serialize(new
        {
            tipo = "pcflow",
            nome = name,
            porta = port,
            protocolo = Protocol,
            deviceId
        });
    }

    public static bool TryParseResponse(string json, string host, long seenAtMs, out PcAnnouncement? announcement)
    {
        announcement = null;
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (!root.TryGetProperty("tipo", out var tipo) || tipo.GetString() != "pcflow")
                return false;
            var deviceId = root.TryGetProperty("deviceId", out var idEl) ? idEl.GetString() ?? "" : "";
            if (string.IsNullOrWhiteSpace(deviceId))
                return false;
            var name = root.TryGetProperty("nome", out var nameEl) ? nameEl.GetString() ?? host : host;
            var port = root.TryGetProperty("porta", out var portEl) && portEl.TryGetInt32(out var parsed) ? parsed : 45456;
            announcement = new PcAnnouncement(deviceId, name, host, port, seenAtMs);
            return true;
        }
        catch (JsonException)
        {
            return false;
        }
    }
}

public sealed class DeviceDirectory
{
    private readonly Dictionary<string, PcAnnouncement> _items = new(StringComparer.Ordinal);

    public IReadOnlyCollection<PcAnnouncement> All => _items.Values.OrderBy(i => i.Name).ToList();

    public void Observe(PcAnnouncement announcement)
    {
        if (_items.TryGetValue(announcement.DeviceId, out var current) && announcement.SeenAtMs < current.SeenAtMs)
            return;
        _items[announcement.DeviceId] = announcement;
    }

    public PcAnnouncement? Latest(string deviceId) =>
        _items.TryGetValue(deviceId, out var item) ? item : null;
}
