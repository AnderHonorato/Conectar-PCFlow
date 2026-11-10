namespace PCFlow.Core.Session;

public enum ConnectionPhase
{
    Idle,
    Discovering,
    DeviceSelected,
    Connecting,
    Authenticating,
    NegotiatingCapabilities,
    Ready,
    Reconnecting,
    Error
}

public enum ConnectionErrorKind
{
    None,
    Permission,
    Network,
    Auth,
    Version,
    Unsupported
}

public enum ConnectionEvent
{
    StartDiscovery,
    SelectDevice,
    BeginConnect,
    SocketUp,
    AuthOk,
    AuthFailed,
    CapabilitiesOk,
    HeartbeatMissed,
    SocketDown,
    PermissionDenied,
    VersionRejected,
    AuthRevoked,
    Reset
}

public sealed record ConnectionSnapshot(ConnectionPhase Phase, ConnectionErrorKind Error)
{
    public bool IsReady => Phase == ConnectionPhase.Ready;
    public bool AcceptsCommands => Phase == ConnectionPhase.Ready;
    public bool AcceptsDestructive => Phase == ConnectionPhase.Ready;

    public static ConnectionSnapshot Idle() => new(ConnectionPhase.Idle, ConnectionErrorKind.None);
}

public sealed class ConnectionMachine
{
    public ConnectionSnapshot Snapshot { get; private set; } = ConnectionSnapshot.Idle();

    public void StartServerSocket() => Snapshot = new(ConnectionPhase.Connecting, ConnectionErrorKind.None);

    public ConnectionSnapshot On(ConnectionEvent ev)
    {
        var phase = Snapshot.Phase;
        Snapshot = (phase, ev) switch
        {
            (ConnectionPhase.Idle, ConnectionEvent.StartDiscovery) => Next(ConnectionPhase.Discovering),
            (ConnectionPhase.Discovering, ConnectionEvent.SelectDevice) => Next(ConnectionPhase.DeviceSelected),
            (ConnectionPhase.Discovering, ConnectionEvent.PermissionDenied) => Fail(ConnectionErrorKind.Permission),
            (ConnectionPhase.DeviceSelected, ConnectionEvent.BeginConnect) => Next(ConnectionPhase.Connecting),
            (ConnectionPhase.Connecting, ConnectionEvent.SocketUp) => Next(ConnectionPhase.Authenticating),
            (ConnectionPhase.Connecting, ConnectionEvent.SocketDown) => Fail(ConnectionErrorKind.Network),
            (ConnectionPhase.Connecting, ConnectionEvent.PermissionDenied) => Fail(ConnectionErrorKind.Permission),
            (ConnectionPhase.Authenticating, ConnectionEvent.AuthOk) => Next(ConnectionPhase.NegotiatingCapabilities),
            (ConnectionPhase.Authenticating, ConnectionEvent.AuthFailed) => Fail(ConnectionErrorKind.Auth),
            (ConnectionPhase.Authenticating, ConnectionEvent.AuthRevoked) => Fail(ConnectionErrorKind.Auth),
            (ConnectionPhase.Authenticating, ConnectionEvent.VersionRejected) => Fail(ConnectionErrorKind.Version),
            (ConnectionPhase.Authenticating, ConnectionEvent.SocketDown) => Fail(ConnectionErrorKind.Network),
            (ConnectionPhase.NegotiatingCapabilities, ConnectionEvent.SocketDown) => Fail(ConnectionErrorKind.Network),
            (ConnectionPhase.NegotiatingCapabilities, ConnectionEvent.CapabilitiesOk) => Next(ConnectionPhase.Ready),
            (ConnectionPhase.NegotiatingCapabilities, ConnectionEvent.VersionRejected) => Fail(ConnectionErrorKind.Version),
            (ConnectionPhase.Ready, ConnectionEvent.HeartbeatMissed) => Next(ConnectionPhase.Reconnecting),
            (ConnectionPhase.Ready, ConnectionEvent.SocketDown) => Next(ConnectionPhase.Reconnecting),
            (ConnectionPhase.Ready, ConnectionEvent.AuthRevoked) => Fail(ConnectionErrorKind.Auth),
            (ConnectionPhase.Reconnecting, ConnectionEvent.SocketUp) => Next(ConnectionPhase.Authenticating),
            (ConnectionPhase.Reconnecting, ConnectionEvent.AuthRevoked) => Fail(ConnectionErrorKind.Auth),
            (ConnectionPhase.Reconnecting, ConnectionEvent.Reset) => ConnectionSnapshot.Idle(),
            (ConnectionPhase.Error, ConnectionEvent.Reset) => ConnectionSnapshot.Idle(),
            (ConnectionPhase.Error, ConnectionEvent.StartDiscovery) => Next(ConnectionPhase.Discovering),
            _ => Snapshot
        };
        return Snapshot;
    }

    private static ConnectionSnapshot Next(ConnectionPhase phase) => new(phase, ConnectionErrorKind.None);
    private static ConnectionSnapshot Fail(ConnectionErrorKind error) => new(ConnectionPhase.Error, error);
}
