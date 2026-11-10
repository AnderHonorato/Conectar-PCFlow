using System.Net;
using Xunit;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using PCFlow.Core.Discovery;
using PCFlow.Core.Protocol;
using PCFlow.Core.Session;
using PCFlow.Core.Trust;

namespace PCFlow.Core.Tests;

public class GoldenFixtureTests
{
    [Theory]
    [InlineData("session-hello.json", "session.hello")]
    [InlineData("session-auth.json", "session.auth")]
    [InlineData("session-capabilities.json", "session.capabilities")]
    [InlineData("session-heartbeat.json", "session.heartbeat")]
    [InlineData("session-ack.json", "session.ack")]
    [InlineData("session-error.json", "session.error")]
    public void RoundTrip(string file, string name)
    {
        var read = ProtocolCodec.Read(Fixture(file));
        Assert.True(read.Success, read.Code);
        Assert.Equal(name, read.Envelope!.Name);
        Assert.Equal(2, read.Envelope.ProtocolVersion);
        var again = ProtocolCodec.Read(ProtocolCodec.Write(read.Envelope));
        Assert.True(again.Success);
        Assert.Equal(read.Envelope.MessageId, again.Envelope!.MessageId);
        Assert.Equal(read.Envelope.Name, again.Envelope.Name);
        Assert.Equal(read.Envelope.Sequence, again.Envelope.Sequence);
        Assert.Equal(read.Envelope.Timestamp, again.Envelope.Timestamp);
    }

    [Fact]
    public void VersionMismatchIsRejected()
    {
        var read = ProtocolCodec.Read(Fixture("version-mismatch.json"));
        Assert.False(read.Success);
        Assert.Equal("PROTOCOL_VERSION_UNSUPPORTED", read.Code);
        Assert.Null(read.Envelope);
    }

    [Fact]
    public void UnknownCommandIsRejected()
    {
        var read = ProtocolCodec.Read(Fixture("unknown-command.json"));
        Assert.False(read.Success);
        Assert.Equal("UNKNOWN_COMMAND", read.Code);
    }

    [Fact]
    public void OversizedPayloadIsRejected()
    {
        var huge = new string('a', ProtocolCodec.MaxBytes + 1);
        var read = ProtocolCodec.Read(huge);
        Assert.Equal("PAYLOAD_TOO_LARGE", read.Code);
    }

    public static string Fixture(string name)
    {
        var dir = Path.Combine(AppContext.BaseDirectory, "fixtures");
        return File.ReadAllText(Path.Combine(dir, name));
    }
}

public class ConnectionMachineTests
{
    [Fact]
    public void ReadyRequiresAuthAndCapabilities()
    {
        var machine = new ConnectionMachine();
        machine.On(ConnectionEvent.CapabilitiesOk);
        Assert.NotEqual(ConnectionPhase.Ready, machine.Snapshot.Phase);
        WalkToReady(machine);
        Assert.Equal(ConnectionPhase.Ready, machine.Snapshot.Phase);
        Assert.Equal(ConnectionErrorKind.None, machine.Snapshot.Error);
    }

    [Fact]
    public void DisconnectInConnectingIsNetworkError()
    {
        var machine = new ConnectionMachine();
        machine.On(ConnectionEvent.StartDiscovery);
        machine.On(ConnectionEvent.SelectDevice);
        machine.On(ConnectionEvent.BeginConnect);
        Assert.Equal(ConnectionPhase.Error, machine.On(ConnectionEvent.SocketDown).Phase);
        Assert.Equal(ConnectionErrorKind.Network, machine.Snapshot.Error);
    }

    [Fact]
    public void DisconnectInReadyGoesReconnecting()
    {
        var machine = new ConnectionMachine();
        WalkToReady(machine);
        Assert.Equal(ConnectionPhase.Reconnecting, machine.On(ConnectionEvent.SocketDown).Phase);
    }

    [Fact]
    public void VersionMismatchAndRevokedAndPermission()
    {
        var version = new ConnectionMachine();
        version.StartServerSocket();
        version.On(ConnectionEvent.SocketUp);
        Assert.Equal(ConnectionErrorKind.Version, version.On(ConnectionEvent.VersionRejected).Error);

        var revoked = new ConnectionMachine();
        WalkToReady(revoked);
        Assert.Equal(ConnectionErrorKind.Auth, revoked.On(ConnectionEvent.AuthRevoked).Error);

        var denied = new ConnectionMachine();
        denied.On(ConnectionEvent.StartDiscovery);
        Assert.Equal(ConnectionErrorKind.Permission, denied.On(ConnectionEvent.PermissionDenied).Error);
    }

    [Fact]
    public void ReconnectContinuesOnNewSocket()
    {
        var machine = new ConnectionMachine();
        WalkToReady(machine);
        machine.On(ConnectionEvent.SocketDown);
        Assert.Equal(ConnectionPhase.Authenticating, machine.On(ConnectionEvent.SocketUp).Phase);
    }

    private static void WalkToReady(ConnectionMachine machine)
    {
        machine.On(ConnectionEvent.StartDiscovery);
        machine.On(ConnectionEvent.SelectDevice);
        machine.On(ConnectionEvent.BeginConnect);
        machine.On(ConnectionEvent.SocketUp);
        machine.On(ConnectionEvent.AuthOk);
        machine.On(ConnectionEvent.CapabilitiesOk);
    }
}

public class TrustTests
{
    [Fact]
    public void DiscoveryDoesNotAuthorizeAndRevokeBlocksToken()
    {
        var store = new TrustStore([]);
        Assert.Equal(TrustDecision.Unknown, store.Authorize("phone-1", "anything"));
        Assert.False(store.TryPair("phone-1", "Pixel", "000000", "123456", out _));
        Assert.True(store.TryPair("phone-1", "Pixel", "123456", "123456", out var token));
        Assert.Equal(TrustDecision.Ok, store.Authorize("phone-1", token));
        Assert.True(store.Revoke("phone-1"));
        Assert.Equal(TrustDecision.Revoked, store.Authorize("phone-1", token));
    }

    [Fact]
    public void SecondSocketCannotInjectBeforeAuth()
    {
        var harness = Harness.Create();
        var early = harness.Engine.Receive(TestCommands.Command("pointer.move", """{"x":3,"y":4}"""), 0);
        Assert.DoesNotContain(early, e => e is SessionEffect.Dispatch);
        Assert.Contains(early, e => e is SessionEffect.Reply reply && reply.Json.Contains("NOT_READY"));
    }
}

public class HeartbeatTests
{
    [Fact]
    public void ThreeMissesLeaveReady()
    {
        var monitor = new HeartbeatMonitor(1_000, 3);
        monitor.Seen(0);
        Assert.False(monitor.Observe(2_999));
        Assert.True(monitor.Observe(3_000));
    }

    [Fact]
    public void MissedHeartbeatLeavesReadySession()
    {
        var harness = Harness.Open();
        Assert.Equal(ConnectionPhase.Ready, harness.Engine.Snapshot.Phase);
        Assert.Empty(harness.Engine.OnHeartbeatClock(2_000));
        Assert.Empty(harness.Engine.OnHeartbeatClock(8_000));
        Assert.Equal(ConnectionPhase.Reconnecting, harness.Engine.Snapshot.Phase);
        var power = harness.Engine.Receive(TestCommands.Command("system.power", """{"action":"shutdown"}"""), 8_000);
        Assert.DoesNotContain(power, e => e is SessionEffect.Dispatch);
    }
}

public class ReconnectTests
{
    [Fact]
    public void StalePointerIsNotReplayed()
    {
        var queue = new OutboundQueue();
        queue.EnqueueMove(Move("a"));
        queue.EnqueueMove(Move("b"));
        queue.EnqueueDiscrete(Button("up"));
        queue.DropStaleMoves();
        var drained = queue.Drain();
        Assert.DoesNotContain(drained, m => m.Name == "pointer.move");
        Assert.Contains(drained, m => m.Name == "pointer.button");
    }

    [Fact]
    public void ButtonDownIsReleasedOnDisconnect()
    {
        var harness = Harness.Open();
        harness.Engine.Receive(TestCommands.Command("pointer.button", """{"button":"left","direction":"down"}"""), 10);
        harness.Engine.Receive(TestCommands.Command("keyboard.key", """{"code":"CTRL","direction":"down"}"""), 11);
        var closed = harness.Engine.OnTransportClosed();
        Assert.Contains(closed, e => e is SessionEffect.ReleaseInput r && r.Kind == "pointer" && r.Code == "left");
        Assert.Contains(closed, e => e is SessionEffect.ReleaseInput r && r.Kind == "key" && r.Code == "CTRL");
        Assert.Equal(ConnectionPhase.Reconnecting, harness.Engine.Snapshot.Phase);
    }

    [Fact]
    public void SameDeviceSurvivesEndpointChange()
    {
        var directory = new DeviceDirectory();
        directory.Observe(new PcAnnouncement("pc-1", "Casa", "10.0.0.2", 45456, 10));
        directory.Observe(new PcAnnouncement("pc-1", "Casa", "10.0.0.9", 45456, 5));
        directory.Observe(new PcAnnouncement("pc-1", "Casa", "10.0.0.8", 45456, 20));
        Assert.Equal("10.0.0.8", directory.Latest("pc-1")!.Host);
    }

    private static ProtocolEnvelope Move(string id) =>
        new(2, id, null, "pointer.move", 1, 1, "sess", """{"x":1,"y":1}""");

    private static ProtocolEnvelope Button(string direction) =>
        new(2, "btn", null, "pointer.button", 1, 2, "sess", $$"""{"button":"left","direction":"{{direction}}"}""");
}

public class DiscoveryTests
{
    [Fact]
    public void ResponseCarriesDeviceId()
    {
        var json = DiscoveryProtocol.Response("Casa", 45456, "pc-1");
        Assert.True(DiscoveryProtocol.TryParseResponse(json, "10.0.0.4", 1, out var found));
        Assert.Equal("pc-1", found!.DeviceId);
        Assert.Equal(2, DiscoveryProtocol.Protocol);
        Assert.False(DiscoveryProtocol.TryParseResponse("""{"tipo":"pcflow","nome":"sem-id"}""", "10.0.0.4", 1, out _));
    }
}

public class SessionFlowTests
{
    [Fact]
    public async Task PairMoveAndReleaseOverTcp()
    {
        var sink = new FakeSink();
        var pasta = Path.Combine(Path.GetTempPath(), "pcflow-tests", Guid.NewGuid().ToString("N"));
        await using var servidor = new ServidorPcFlow(sink, 0, 0, new ArmazenamentoConfiguracao(pasta), IPAddress.Loopback);
        await servidor.IniciarAsync();
        using var tcp = new TcpClient();
        await tcp.ConnectAsync(IPAddress.Loopback, servidor.PortaControle);
        await using var stream = tcp.GetStream();
        using var reader = new StreamReader(stream, Encoding.UTF8, false, leaveOpen: true);
        await using var writer = new StreamWriter(stream, new UTF8Encoding(false), leaveOpen: true) { AutoFlush = true };

        await writer.WriteLineAsync(Hello("phone-1"));
        var required = ProtocolCodec.Read(await reader.ReadLineAsync() ?? "");
        Assert.Equal("AUTH_REQUIRED", ProtocolCodec.PayloadString(required.Envelope!, "code"));

        await writer.WriteLineAsync(Auth("phone-1", servidor.CodigoPareamento));
        var ack = ProtocolCodec.Read(await reader.ReadLineAsync() ?? "");
        Assert.Equal("session.ack", ack.Envelope!.Name);
        Assert.False(string.IsNullOrWhiteSpace(ProtocolCodec.PayloadString(ack.Envelope, "token")));
        var caps = ProtocolCodec.Read(await reader.ReadLineAsync() ?? "");
        Assert.Equal("session.capabilities", caps.Envelope!.Name);

        await writer.WriteLineAsync(CommandLine("pointer.button", """{"button":"left","direction":"down"}"""));
        Assert.Equal("session.ack", ProtocolCodec.Read(await reader.ReadLineAsync() ?? "").Envelope!.Name);
        tcp.Client.Shutdown(SocketShutdown.Send);
        var until = DateTime.UtcNow.AddSeconds(3);
        while (DateTime.UtcNow < until && sink.Releases.Count == 0)
            await Task.Delay(20);
        Assert.Contains(sink.Commands, c => c.Name == "pointer.button" && c.Direction == "down");
        Assert.Contains(sink.Releases, r => r == ("pointer", "left"));
    }

    private static string Hello(string id) => ProtocolCodec.Write(new ProtocolEnvelope(2, "m1", null, "session.hello", 10, 1, null, JsonSerializer.Serialize(new { deviceId = id, deviceName = "Pixel" })));

    private static string Auth(string id, string pin) => ProtocolCodec.Write(new ProtocolEnvelope(2, "m2", null, "session.auth", 11, 2, null, JsonSerializer.Serialize(new { deviceId = id, deviceName = "Pixel", pin })));

    private static string CommandLine(string name, string payload) => ProtocolCodec.Write(new ProtocolEnvelope(2, Guid.NewGuid().ToString("D"), null, name, 12, 3, null, payload));
}

public sealed class FakeSink : ICommandSink
{
    public List<RemoteCommand> Commands { get; } = [];
    public List<(string Kind, string Code)> Releases { get; } = [];
    public void Execute(RemoteCommand command) => Commands.Add(command);
    public void ReleaseHeld(string kind, string code) => Releases.Add((kind, code));
}

public sealed class Harness
{
    public SessionEngine Engine { get; }
    public string Pin { get; private set; } = "123456";
    private Harness(SessionEngine engine) => Engine = engine;

    public static Harness Create()
    {
        Harness? harness = null;
        var engine = new SessionEngine(new TrustStore([]), "pc-1", () => harness!.Pin, () => harness!.Pin = "654321", new SessionGate());
        harness = new Harness(engine);
        return harness;
    }

    public static Harness Open()
    {
        var harness = Create();
        harness.Engine.OnSocketAccepted();
        harness.Engine.MarkSocketUp();
        var hello = harness.Engine.Receive(ProtocolCodec.Write(new ProtocolEnvelope(2, "h", null, "session.hello", 1, 1, null, """{"deviceId":"phone-1","deviceName":"Pixel"}""")), 0);
        Assert.Contains(hello, e => e is SessionEffect.Reply r && r.Json.Contains("AUTH_REQUIRED"));
        var auth = harness.Engine.Receive(ProtocolCodec.Write(new ProtocolEnvelope(2, "a", null, "session.auth", 2, 2, null, """{"deviceId":"phone-1","deviceName":"Pixel","pin":"123456"}""")), 0);
        Assert.Contains(auth, e => e is SessionEffect.Reply r && r.Json.Contains("session.capabilities"));
        Assert.Equal(ConnectionPhase.Ready, harness.Engine.Snapshot.Phase);
        return harness;
    }
}

static class TestCommands
{
    public static string Command(string name, string payload) =>
        ProtocolCodec.Write(new ProtocolEnvelope(2, Guid.NewGuid().ToString("D"), null, name, 5, 4, null, payload));
}
