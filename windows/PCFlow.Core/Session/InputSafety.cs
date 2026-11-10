namespace PCFlow.Core.Session;

public sealed class InputSafety
{
    private readonly HashSet<string> _held = new(StringComparer.Ordinal);

    public IReadOnlyCollection<string> Held => _held;

    public void Down(string kind, string code) => _held.Add(Key(kind, code));

    public void Up(string kind, string code) => _held.Remove(Key(kind, code));

    public IReadOnlyList<(string Kind, string Code)> ReleaseAll()
    {
        var items = _held.Select(Parse).ToList();
        _held.Clear();
        return items;
    }

    private static string Key(string kind, string code) => kind + ":" + code;

    private static (string Kind, string Code) Parse(string value)
    {
        var split = value.Split(':', 2);
        return (split[0], split.Length > 1 ? split[1] : "");
    }
}
