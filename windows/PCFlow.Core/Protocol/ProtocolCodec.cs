using System.Globalization;
using System.Text;
using System.Text.Json;

namespace PCFlow.Core.Protocol;

public static class ProtocolCodec
{
    public const int CurrentVersion = 2;
    public const int MaxBytes = 65_536;

    public static readonly HashSet<string> KnownNames = new(StringComparer.Ordinal)
    {
        "session.hello", "session.auth", "session.capabilities", "session.heartbeat",
        "session.ack", "session.error",
        "pointer.move", "pointer.button", "pointer.scroll",
        "keyboard.text", "keyboard.key",
        "media.transport", "media.volume",
        "system.power"
    };

    public static ProtocolRead Read(string json)
    {
        if (Encoding.UTF8.GetByteCount(json) > MaxBytes)
            return ProtocolRead.Fail("PAYLOAD_TOO_LARGE");
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
                return ProtocolRead.Fail("INVALID_MESSAGE");
            if (!root.TryGetProperty("protocolVersion", out var versionEl) || versionEl.ValueKind != JsonValueKind.Number || !versionEl.TryGetInt32(out var version))
                return ProtocolRead.Fail("PROTOCOL_VERSION_UNSUPPORTED");
            if (version != CurrentVersion)
                return ProtocolRead.Fail("PROTOCOL_VERSION_UNSUPPORTED");
            if (!TryString(root, "messageId", out var messageId) || !TryString(root, "name", out var name))
                return ProtocolRead.Fail("INVALID_MESSAGE");
            if (!KnownNames.Contains(name))
                return ProtocolRead.Fail("UNKNOWN_COMMAND");
            if (!root.TryGetProperty("timestamp", out var tsEl) || tsEl.ValueKind != JsonValueKind.Number || !tsEl.TryGetInt64(out var timestamp))
                return ProtocolRead.Fail("INVALID_MESSAGE");
            if (!root.TryGetProperty("sequence", out var seqEl) || seqEl.ValueKind != JsonValueKind.Number || !seqEl.TryGetInt64(out var sequence) || sequence < 0)
                return ProtocolRead.Fail("INVALID_MESSAGE");
            string? correlationId = OptionalString(root, "correlationId");
            string? sessionId = OptionalString(root, "sessionId");
            var payload = root.TryGetProperty("payload", out var payloadEl) && payloadEl.ValueKind == JsonValueKind.Object
                ? payloadEl.GetRawText()
                : "{}";
            return ProtocolRead.Ok(new ProtocolEnvelope(version, messageId, correlationId, name, timestamp, sequence, sessionId, payload));
        }
        catch (JsonException)
        {
            return ProtocolRead.Fail("INVALID_MESSAGE");
        }
    }

    public static string Write(ProtocolEnvelope message)
    {
        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            writer.WriteStartObject();
            writer.WriteNumber("protocolVersion", message.ProtocolVersion);
            writer.WriteString("messageId", message.MessageId);
            if (!string.IsNullOrEmpty(message.CorrelationId))
                writer.WriteString("correlationId", message.CorrelationId);
            writer.WriteString("name", message.Name);
            writer.WriteNumber("timestamp", message.Timestamp);
            writer.WriteNumber("sequence", message.Sequence);
            if (!string.IsNullOrEmpty(message.SessionId))
                writer.WriteString("sessionId", message.SessionId);
            writer.WritePropertyName("payload");
            using var payload = JsonDocument.Parse(string.IsNullOrWhiteSpace(message.PayloadJson) ? "{}" : message.PayloadJson);
            payload.RootElement.WriteTo(writer);
            writer.WriteEndObject();
        }
        return Encoding.UTF8.GetString(stream.ToArray());
    }

    public static string PayloadString(ProtocolEnvelope message, string property)
    {
        using var doc = JsonDocument.Parse(message.PayloadJson);
        return doc.RootElement.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? ""
            : "";
    }

    public static long PayloadLong(ProtocolEnvelope message, string property)
    {
        using var doc = JsonDocument.Parse(message.PayloadJson);
        return doc.RootElement.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.Number && value.TryGetInt64(out var number)
            ? number
            : 0;
    }

    public static double PayloadDouble(ProtocolEnvelope message, string property)
    {
        using var doc = JsonDocument.Parse(message.PayloadJson);
        return doc.RootElement.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.Number && value.TryGetDouble(out var number)
            ? number
            : 0;
    }

    public static string Error(string code, string messageId, string? correlationId, long timestamp, long sequence, string text)
    {
        var payload = JsonSerializer.Serialize(new Dictionary<string, string> { ["code"] = code, ["message"] = text });
        return Write(new ProtocolEnvelope(CurrentVersion, messageId, correlationId, "session.error", timestamp, sequence, null, payload));
    }

    private static bool TryString(JsonElement root, string name, out string value)
    {
        value = "";
        if (!root.TryGetProperty(name, out var el) || el.ValueKind != JsonValueKind.String)
            return false;
        value = el.GetString() ?? "";
        return value.Length > 0;
    }

    private static string? OptionalString(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var el) || el.ValueKind != JsonValueKind.String)
            return null;
        var text = el.GetString();
        return string.IsNullOrEmpty(text) ? null : text;
    }
}

public sealed record ProtocolEnvelope(
    int ProtocolVersion,
    string MessageId,
    string? CorrelationId,
    string Name,
    long Timestamp,
    long Sequence,
    string? SessionId,
    string PayloadJson);

public sealed record ProtocolRead(bool Success, string Code, ProtocolEnvelope? Envelope)
{
    public static ProtocolRead Ok(ProtocolEnvelope envelope) => new(true, "", envelope);
    public static ProtocolRead Fail(string code) => new(false, code, null);
}

public static class ProtocolTexts
{
    public static string For(string code) => code switch
    {
        "PROTOCOL_VERSION_UNSUPPORTED" => "Atualize o app do PC ou do celular.",
        "AUTH_REQUIRED" => "Informe o código de pareamento mostrado no computador.",
        "AUTH_REJECTED" => "Código ou token recusado.",
        "AUTH_REVOKED" => "Este aparelho foi revogado. Pareie de novo.",
        "NOT_READY" => "A sessão ainda não está pronta.",
        "SESSION_IN_USE" => "Já existe um controle ativo neste computador.",
        "UNKNOWN_COMMAND" => "Comando não reconhecido.",
        "PAYLOAD_TOO_LARGE" => "A mensagem passou do limite.",
        "INVALID_MESSAGE" => "Mensagem inválida.",
        _ => "Falha de sessão."
    };

    public static string NewId() => Guid.NewGuid().ToString("D", CultureInfo.InvariantCulture);
}
