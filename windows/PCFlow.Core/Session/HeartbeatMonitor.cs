namespace PCFlow.Core.Session;

public sealed class HeartbeatMonitor
{
    private readonly long _intervalMs;
    private readonly int _missLimit;
    private long _lastSeen;
    private int _misses;
    private bool _started;

    public HeartbeatMonitor(long intervalMs, int missLimit)
    {
        _intervalMs = intervalMs;
        _missLimit = missLimit;
    }

    public void Seen(long nowMs)
    {
        _lastSeen = nowMs;
        _misses = 0;
        _started = true;
    }

    public bool Observe(long nowMs)
    {
        if (!_started)
            Seen(nowMs);
        while (nowMs - _lastSeen >= _intervalMs)
        {
            _misses++;
            _lastSeen += _intervalMs;
            if (_misses >= _missLimit)
                return true;
        }
        return false;
    }
}
