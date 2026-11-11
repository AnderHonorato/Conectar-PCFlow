namespace FlowTutum.Next;

/// <summary>
/// Acesso automatico apenas na LAN domestica configurada.
/// Alteracao local persistente pela bandeja do Windows, sem servidor de administracao remoto.
/// </summary>
public static class LocalAccessSettings {
    private static bool allowedOnStart;
    private static readonly object Gate=new();
    public static string FilePath=>Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "FlowTutumNext","wifi-direto-desativado.flag");

    public static void Configure(bool allowed)=>allowedOnStart=allowed;
    public static bool Enabled=>allowedOnStart&&!File.Exists(FilePath);
    public static bool Available=>allowedOnStart;
    public static void SetEnabled(bool enabled) {
        lock(Gate) {
            if(!allowedOnStart)throw new InvalidOperationException("Wi-Fi direto nao habilitado neste PC.");
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            if(enabled)File.Delete(FilePath);
            else File.WriteAllText(FilePath,DateTime.UtcNow.ToString("O"));
        }
    }
}
