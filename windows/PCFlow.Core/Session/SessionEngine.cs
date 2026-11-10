using System.Text.Json;
using PCFlow.Core.Protocol;
using PCFlow.Core.Trust;

namespace PCFlow.Core.Session;

public sealed class SessionGate
{
    private int _holder;

    public bool TryEnter() => Interlocked.CompareExchange(ref _holder, 1, 0) == 0;

    public void Exit() => Interlocked.Exchange(ref _holder, 0);
}

public abstract record SessionEffect
{
    public sealed record Reply(string Json) : SessionEffect;
    public sealed record Dispatch(RemoteCommand Command) : SessionEffect;
    public sealed record ReleaseInput(string Kind, string Code) : SessionEffect;
    public sealed record CloseSocket() : SessionEffect;
}

public sealed class SessionEngine
{
    private readonly TrustStore _trust;
    private readonly string _hostDeviceId;
    private readonly Func<string> _currentPin;
    private readonly Action _rotatePin;
    private readonly SessionGate _gate;
    private readonly ConnectionMachine _machine = new();
    private readonly InputSafety _safety = new();
    private readonly HeartbeatMonitor _heartbeat = new(2_000, 3);
    private bool _holdsGate;
    private string? _sessionId;
    private string? _deviceId;
    private long _sequence = 1;
    private bool _trustChanged;

    public SessionEngine(TrustStore trust, string hostDeviceId, Func<string> currentPin, Action rotatePin, SessionGate gate)
    {
        _trust = trust;
        _hostDeviceId = hostDeviceId;
        _currentPin = currentPin;
        _rotatePin = rotatePin;
        _gate = gate;
    }

    public ConnectionSnapshot Snapshot => _machine.Snapshot;
    public bool TrustChanged => _trustChanged;

    public void OnSocketAccepted() => _machine.StartServerSocket();

    public void MarkSocketUp() => _machine.On(ConnectionEvent.SocketUp);

    public IReadOnlyList<SessionEffect> Receive(string line, long nowMs)
    {
        var effects = new List<SessionEffect>();
        if (LegacyAdapter.TryTranslate(line, out var translated, out var legacyCommand))
        {
            if (legacyCommand is not null)
            {
                if (!_machine.Snapshot.AcceptsCommands)
                {
                    effects.Add(Error("NOT_READY", null));
                    return effects;
                }
                effects.Add(new SessionEffect.Dispatch(legacyCommand));
                return effects;
            }
            line = translated;
        }

        var read = ProtocolCodec.Read(line);
        if (!read.Success || read.Envelope is null)
        {
            if (read.Code == "PROTOCOL_VERSION_UNSUPPORTED")
                _machine.On(ConnectionEvent.VersionRejected);
            effects.Add(Error(read.Code, null));
            if (read.Code is "PROTOCOL_VERSION_UNSUPPORTED" or "UNKNOWN_COMMAND" or "PAYLOAD_TOO_LARGE")
                effects.Add(new SessionEffect.CloseSocket());
            return effects;
        }

        var message = read.Envelope;
        switch (message.Name)
        {
            case "session.hello":
                effects.AddRange(Hello(message, nowMs));
                break;
            case "session.auth":
                effects.AddRange(Auth(message, nowMs));
                break;
            case "session.heartbeat":
                effects.AddRange(Heartbeat(message, nowMs));
                break;
            case "session.ack":
            case "session.capabilities":
            case "session.error":
                break;
            default:
                effects.AddRange(Command(message));
                break;
        }
        return effects;
    }

    public IReadOnlyList<SessionEffect> OnHeartbeatClock(long nowMs)
    {
        if (_machine.Snapshot.Phase != ConnectionPhase.Ready)
            return [];
        if (!_heartbeat.Observe(nowMs))
            return [];
        _machine.On(ConnectionEvent.HeartbeatMissed);
        return [];
    }

    public IReadOnlyList<SessionEffect> OnTransportClosed()
    {
        var effects = _safety.ReleaseAll()
            .Select(item => (SessionEffect)new SessionEffect.ReleaseInput(item.Kind, item.Code))
            .ToList();
        _machine.On(ConnectionEvent.SocketDown);
        ReleaseGate();
        return effects;
    }

    public IReadOnlyList<SessionEffect> Revoke(string deviceId)
    {
        if (!_trust.Revoke(deviceId))
            return [];
        _trustChanged = true;
        if (!string.Equals(_deviceId, deviceId, StringComparison.Ordinal))
            return [];
        _machine.On(ConnectionEvent.AuthRevoked);
        ReleaseGate();
        return [Error("AUTH_REVOKED", null), new SessionEffect.CloseSocket()];
    }

    private IEnumerable<SessionEffect> Hello(ProtocolEnvelope message, long nowMs)
    {
        var deviceId = ProtocolCodec.PayloadString(message, "deviceId");
        var deviceName = ProtocolCodec.PayloadString(message, "deviceName");
        var token = ProtocolCodec.PayloadString(message, "token");
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            yield return Error("INVALID_MESSAGE", message.MessageId);
            yield break;
        }
        _deviceId = deviceId;
        if (_machine.Snapshot.Phase is ConnectionPhase.Connecting or ConnectionPhase.Reconnecting)
            _machine.On(ConnectionEvent.SocketUp);
        if (string.IsNullOrEmpty(token))
        {
            yield return Error("AUTH_REQUIRED", message.MessageId);
            yield break;
        }
        switch (_trust.Authorize(deviceId, token))
        {
            case TrustDecision.Ok:
                foreach (var effect in EnterReady(message, deviceId, nowMs))
                    yield return effect;
                break;
            case TrustDecision.Revoked:
                _machine.On(ConnectionEvent.AuthRevoked);
                ReleaseGate();
                yield return Error("AUTH_REVOKED", message.MessageId);
                yield return new SessionEffect.CloseSocket();
                break;
            default:
                _machine.On(ConnectionEvent.AuthFailed);
                yield return Error("AUTH_REJECTED", message.MessageId);
                yield return new SessionEffect.CloseSocket();
                break;
        }
    }

    private IEnumerable<SessionEffect> Auth(ProtocolEnvelope message, long nowMs)
    {
        var deviceId = ProtocolCodec.PayloadString(message, "deviceId");
        var deviceName = ProtocolCodec.PayloadString(message, "deviceName");
        var pin = ProtocolCodec.PayloadString(message, "pin");
        if (string.IsNullOrWhiteSpace(deviceId))
            deviceId = _deviceId ?? "";
        if (!_trust.TryPair(deviceId, deviceName, pin, _currentPin(), out var token))
        {
            _machine.On(ConnectionEvent.AuthFailed);
            yield return Error("AUTH_REJECTED", message.MessageId);
            yield return new SessionEffect.CloseSocket();
            yield break;
        }
        _trustChanged = true;
        _rotatePin();
        _deviceId = deviceId;
        var payload = JsonSerializer.Serialize(new Dictionary<string, string> { ["token"] = token, ["hostDeviceId"] = _hostDeviceId });
        yield return Reply("session.ack", message.MessageId, payload, nowMs);
        foreach (var effect in EnterReady(message, deviceId, nowMs))
            yield return effect;
    }

    private IEnumerable<SessionEffect> EnterReady(ProtocolEnvelope message, string deviceId, long nowMs)
    {
        if (!_holdsGate && !_gate.TryEnter())
        {
            yield return Error("SESSION_IN_USE", message.MessageId);
            yield return new SessionEffect.CloseSocket();
            yield break;
        }
        _holdsGate = true;
        _sessionId = ProtocolTexts.NewId();
        _trust.Touch(deviceId);
        _trustChanged = true;
        _machine.On(ConnectionEvent.AuthOk);
        var payload = JsonSerializer.Serialize(new
        {
            hostDeviceId = _hostDeviceId,
            capabilities = new[]
            {
                new { name = "pointer.move", state = "supported" },
                new { name = "pointer.button", state = "supported" },
                new { name = "pointer.scroll", state = "supported" },
                new { name = "keyboard.text", state = "supported" },
                new { name = "keyboard.key", state = "supported" },
                new { name = "media.transport", state = "supported" },
                new { name = "media.volume", state = "supported" },
                new { name = "system.power", state = "supported" }
            }
        });
        yield return Reply("session.capabilities", message.MessageId, payload, nowMs);
        _machine.On(ConnectionEvent.CapabilitiesOk);
        _heartbeat.Seen(nowMs);
    }

    private IEnumerable<SessionEffect> Heartbeat(ProtocolEnvelope message, long nowMs)
    {
        if (!_machine.Snapshot.IsReady)
        {
            yield return Error("NOT_READY", message.MessageId);
            yield break;
        }
        _heartbeat.Seen(nowMs);
        var payload = JsonSerializer.Serialize(new Dictionary<string, long> { ["rttMs"] = 0 });
        yield return Reply("session.ack", message.MessageId, payload, nowMs);
    }

    private IEnumerable<SessionEffect> Command(ProtocolEnvelope message)
    {
        if (!_machine.Snapshot.AcceptsCommands)
        {
            yield return Error("NOT_READY", message.MessageId);
            yield break;
        }
        if (message.Name == "system.power" && !_machine.Snapshot.AcceptsDestructive)
        {
            yield return Error("NOT_READY", message.MessageId);
            yield break;
        }
        TrackSafety(message);
        var command = ToCommand(message);
        if (command is null)
        {
            yield return Error("INVALID_MESSAGE", message.MessageId);
            yield break;
        }
        yield return new SessionEffect.Dispatch(command);
        yield return Reply("session.ack", message.MessageId, "{}", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
    }

    private void TrackSafety(ProtocolEnvelope message)
    {
        var direction = ProtocolCodec.PayloadString(message, "direction");
        if (message.Name == "pointer.button")
        {
            var button = ProtocolCodec.PayloadString(message, "button");
            if (direction == "down") _safety.Down("pointer", button);
            if (direction == "up") _safety.Up("pointer", button);
        }
        else if (message.Name == "keyboard.key")
        {
            var key = ProtocolCodec.PayloadString(message, "code");
            if (direction == "down") _safety.Down("key", key);
            if (direction == "up") _safety.Up("key", key);
        }
    }

    private static RemoteCommand? ToCommand(ProtocolEnvelope message)
    {
        return message.Name switch
        {
            "pointer.move" => new RemoteCommand("pointer.move", X: ProtocolCodec.PayloadDouble(message, "x"), Y: ProtocolCodec.PayloadDouble(message, "y")),
            "pointer.button" => new RemoteCommand("pointer.button", Button: ProtocolCodec.PayloadString(message, "button"), Direction: ProtocolCodec.PayloadString(message, "direction")),
            "pointer.scroll" => new RemoteCommand("pointer.scroll", Delta: (int)ProtocolCodec.PayloadLong(message, "delta")),
            "keyboard.text" => new RemoteCommand("keyboard.text", Text: ProtocolCodec.PayloadString(message, "text")),
            "keyboard.key" => new RemoteCommand("keyboard.key", Key: ProtocolCodec.PayloadString(message, "code"), Direction: ProtocolCodec.PayloadString(message, "direction")),
            "media.transport" => new RemoteCommand("media.transport", Action: ProtocolCodec.PayloadString(message, "action")),
            "media.volume" => new RemoteCommand("media.volume", Action: ProtocolCodec.PayloadString(message, "action")),
            "system.power" => new RemoteCommand("system.power", Action: ProtocolCodec.PayloadString(message, "action")),
            _ => null
        };
    }

    private SessionEffect Reply(string name, string? correlationId, string payload, long nowMs)
    {
        var envelope = new ProtocolEnvelope(ProtocolCodec.CurrentVersion, ProtocolTexts.NewId(), correlationId, name, nowMs, _sequence++, _sessionId, payload);
        return new SessionEffect.Reply(ProtocolCodec.Write(envelope));
    }

    private SessionEffect Error(string code, string? correlationId) =>
        new SessionEffect.Reply(ProtocolCodec.Error(code, ProtocolTexts.NewId(), correlationId, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(), _sequence++, ProtocolTexts.For(code)));

    private void ReleaseGate()
    {
        if (!_holdsGate)
            return;
        _gate.Exit();
        _holdsGate = false;
    }
}

public static class LegacyAdapter
{
    public static bool TryTranslate(string line, out string translated, out RemoteCommand? command)
    {
        translated = line;
        command = null;
        try
        {
            using var doc = JsonDocument.Parse(line);
            var root = doc.RootElement;
            if (root.TryGetProperty("protocolVersion", out _))
                return false;
            if (!root.TryGetProperty("tipo", out var tipoEl))
                return false;
            var tipo = tipoEl.GetString() ?? "";
            if (tipo == "ola")
            {
                var deviceId = root.TryGetProperty("dispositivoId", out var id) ? id.GetString() ?? "" : "";
                var name = root.TryGetProperty("nome", out var nome) ? nome.GetString() ?? "" : "";
                var token = root.TryGetProperty("token", out var tok) ? tok.GetString() ?? "" : "";
                var pin = root.TryGetProperty("pin", out var p) ? p.GetString() ?? "" : "";
                if (!string.IsNullOrEmpty(pin))
                {
                    translated = ProtocolCodec.Write(new ProtocolEnvelope(
                        ProtocolCodec.CurrentVersion, ProtocolTexts.NewId(), null, "session.auth",
                        DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(), 1, null,
                        JsonSerializer.Serialize(new Dictionary<string, string> { ["deviceId"] = deviceId, ["deviceName"] = name, ["pin"] = pin })));
                    return true;
                }
                var payload = new Dictionary<string, string> { ["deviceId"] = deviceId, ["deviceName"] = name };
                if (!string.IsNullOrEmpty(token))
                    payload["token"] = token;
                translated = ProtocolCodec.Write(new ProtocolEnvelope(
                    ProtocolCodec.CurrentVersion, ProtocolTexts.NewId(), null, "session.hello",
                    DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(), 1, null,
                    JsonSerializer.Serialize(payload)));
                return true;
            }
            command = tipo switch
            {
                "mouse_move" => new RemoteCommand("pointer.move", X: Number(root, "x"), Y: Number(root, "y")),
                "mouse_click" => new RemoteCommand("pointer.button", Button: Text(root, "botao"), Direction: "click"),
                "scroll" => new RemoteCommand("pointer.scroll", Delta: (int)Number(root, "delta")),
                "texto" => new RemoteCommand("keyboard.text", Text: Text(root, "texto")),
                "tecla" => new RemoteCommand("keyboard.key", Key: Text(root, "tecla"), Direction: "click"),
                "media" => new RemoteCommand("media.transport", Action: Text(root, "acao")),
                "power" => new RemoteCommand("system.power", Action: Text(root, "acao")),
                _ => null
            };
            return command is not null;
        }
        catch (JsonException)
        {
            return false;
        }
    }

    private static string Text(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";

    private static double Number(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number && value.TryGetDouble(out var number) ? number : 0;
}
