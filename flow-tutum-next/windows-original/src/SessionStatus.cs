using System.Diagnostics;
using System.Runtime.InteropServices;

namespace FlowTutum.Next;

/// <summary>
/// Representa a disponibilidade da sessao Windows do proprio processo.
/// O servidor pode continuar vivo mesmo quando o desktop foi desconectado.
/// </summary>
public static class SessionStatus {
    private const int WtsConnectState=8;
    private const int WtsActive=0;
    [DllImport("wtsapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]
    private static extern bool WTSQuerySessionInformation(
        nint server,int sessionId,int infoClass,out nint buffer,out int bytesReturned);
    [DllImport("wtsapi32.dll")]
    private static extern void WTSFreeMemory(nint buffer);
    public record Snapshot(bool Available,string Code,string Message,int SessionId,int State);

    public static Snapshot Read() {
        int session=Process.GetCurrentProcess().SessionId;
        nint buffer=0;
        try {
            if(!WTSQuerySessionInformation(0,session,WtsConnectState,out buffer,out int size)||
               buffer==0||size<sizeof(int)){
                return new Snapshot(false,"session-unknown",
                    "Nao foi possivel verificar a sessao Windows. Confira o usuario no PC.",session,-1);
            }
            int status=Marshal.ReadInt32(buffer);
            return status==WtsActive
                ?new Snapshot(true,"active","Desktop Windows ativo",session,status)
                :new Snapshot(false,status==4?"disconnected":"inactive",
                    status==4?"A sessao Windows esta desconectada. Entre novamente nesta conta no PC.":
                    "Esta conta Windows nao possui desktop ativo neste momento.",session,status);
        } catch(Exception ex) when(ex is DllNotFoundException or EntryPointNotFoundException) {
            return new Snapshot(false,"unsupported","Consulta de sessao Windows indisponivel.",session,-1);
        } finally{if(buffer!=0)WTSFreeMemory(buffer);}
    }
}
