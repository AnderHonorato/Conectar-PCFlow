using PCFlow.Core.Protocol;

namespace PCFlow.Core.Session;

public sealed class OutboundQueue
{
    private ProtocolEnvelope? _pendingMove;
    private readonly Queue<ProtocolEnvelope> _discrete = new();

    public int CoalescedMoves { get; private set; }

    public void EnqueueMove(ProtocolEnvelope move)
    {
        if (_pendingMove is not null)
            CoalescedMoves++;
        _pendingMove = move;
    }

    public void EnqueueDiscrete(ProtocolEnvelope message) => _discrete.Enqueue(message);

    public void DropStaleMoves() => _pendingMove = null;

    public IReadOnlyList<ProtocolEnvelope> Drain()
    {
        var list = new List<ProtocolEnvelope>();
        if (_pendingMove is not null)
        {
            list.Add(_pendingMove);
            _pendingMove = null;
        }
        while (_discrete.Count > 0)
            list.Add(_discrete.Dequeue());
        return list;
    }
}
