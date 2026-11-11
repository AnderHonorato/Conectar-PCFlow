using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace FlowTutum.Next;

public sealed class InputEngine {
    [DllImport("user32.dll", SetLastError=true)] private static extern bool SetCursorPos(int x,int y);
    [DllImport("user32.dll", SetLastError=true)] private static extern bool GetCursorPos(out POINT p);
    [DllImport("user32.dll", SetLastError=true)] private static extern uint SendInput(uint n, INPUT[] events,int size);
    [StructLayout(LayoutKind.Sequential)] private struct POINT {public int X,Y;}
    [StructLayout(LayoutKind.Sequential)] private struct INPUT {public uint Type; public MOUSEINPUT Mouse;}
    [StructLayout(LayoutKind.Sequential)] private struct MOUSEINPUT {
        public int Dx,Dy;public uint MouseData,Flags,Time;public nuint ExtraInfo;
    }
    private const uint LEFTDOWN=0x0002,LEFTUP=0x0004,RIGHTDOWN=0x0008,RIGHTUP=0x0010,
        MIDDLEDOWN=0x0020,MIDDLEUP=0x0040,WHEEL=0x0800,HWHEEL=0x1000;
    private readonly object gate = new();
    private bool leftDown,rightDown,middleDown;
    private string? owner;
    private static void Send(uint flags,int amount=0) {
        var input=new INPUT {Type=0,Mouse=new MOUSEINPUT {Flags=flags,MouseData=unchecked((uint)amount)}};
        if (SendInput(1,new []{input},Marshal.SizeOf<INPUT>())!=1)
            throw new InvalidOperationException("Windows rejeitou entrada fisica: "+Marshal.GetLastWin32Error());
    }
    public static MonitorGeometry[] Monitors() => Screen.AllScreens.Select(
        m=>new MonitorGeometry(m.Bounds.Left,m.Bounds.Top,m.Bounds.Width,m.Bounds.Height)).ToArray();
    public static ScreenPoint PointFor(double x,double y,int monitor) {
        var all=Monitors();
        if(monitor<0 || monitor>=all.Length) throw new ArgumentOutOfRangeException(nameof(monitor));
        return Coordinates.FromNormalized(x,y,all[monitor]);
    }
    public void Apply(string session,string phase,int monitor,double x,double y,string button,int wheel=0) {
        lock(gate) {
            if(phase=="cancel"){Release(session);return;}
            if(phase!="move" && phase!="tap" && phase!="down" && phase!="up" && phase!="wheel" && phase!="hwheel")
                throw new ArgumentException("Fase de ponteiro invalida.");
            if(button!="left"&&button!="right"&&button!="middle")throw new ArgumentException("Botao invalido.");
            if(owner!=null && owner!=session && (leftDown||rightDown||middleDown))
                throw new InvalidOperationException("Outro participante esta arrastando.");
            if(phase=="wheel"||phase=="hwheel"){
                Send(phase=="wheel"?WHEEL:HWHEEL,Math.Clamp(wheel,-1200,1200));return;
            }
            var point=PointFor(x,y,monitor);
            if(!SetCursorPos(point.X,point.Y))throw new InvalidOperationException("Windows nao moveu o cursor.");
            if(phase=="move")return;
            bool pressed=button=="left"?leftDown:button=="right"?rightDown:middleDown;
            uint downFlag=button=="left"?LEFTDOWN:button=="right"?RIGHTDOWN:MIDDLEDOWN;
            uint upFlag=button=="left"?LEFTUP:button=="right"?RIGHTUP:MIDDLEUP;
            if(phase=="down"||phase=="tap") {
                if(pressed)throw new InvalidOperationException("Botao ja pressionado.");
                owner=session;Send(downFlag);
                if(button=="left")leftDown=true;
                else if(button=="right")rightDown=true;
                else middleDown=true;
            }
            if(phase=="up"||phase=="tap") {
                if((button=="left"&&leftDown)||(button=="right"&&rightDown)||(button=="middle"&&middleDown)){
                    Send(upFlag);
                    if(button=="left")leftDown=false;
                    else if(button=="right")rightDown=false;
                    else middleDown=false;
                }
                if(!leftDown&&!rightDown&&!middleDown)owner=null;
            }
        }
    }
    public void Release(string session) {
        lock(gate) {
            if(owner!=session)return;
            bool releaseLeft=leftDown,releaseRight=rightDown,releaseMiddle=middleDown;
            leftDown=rightDown=middleDown=false;owner=null;
            // Nunca conservar um arraste preso ao perder a rede/sessao.
            try {
                if(releaseLeft)Send(LEFTUP);
            } finally {
                try{if(releaseRight)Send(RIGHTUP);}
                finally{if(releaseMiddle)Send(MIDDLEUP);}
            }
        }
    }
    public void ReleaseAll() {
        lock(gate){
            if(owner is not null)Release(owner);
        }
    }
    public static object? CursorLocation() {
        if(!GetCursorPos(out var point))return null;
        var screens=Monitors();
        for(int i=0;i<screens.Length;i++) {
            var s=screens[i];
            if(point.X>=s.Left&&point.X<s.Left+s.Width&&point.Y>=s.Top&&point.Y<s.Top+s.Height) {
                return new {screen=i,x=(point.X-s.Left)/(double)Math.Max(1,s.Width-1),
                    y=(point.Y-s.Top)/(double)Math.Max(1,s.Height-1)};
            }
        }
        return null;
    }
}
