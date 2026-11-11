using System.Runtime.InteropServices;

namespace FlowTutum.Next;

/// <summary>
/// Entrada por teclado nativo com pares DOWN/UP completos e suporte Unicode.
/// Nao envia teclas enquanto a sessao Windows estiver indisponivel.
/// </summary>
public static class KeyboardEngine {
    public readonly record struct KeyStroke(ushort VirtualKey, ushort ScanCode,uint Flags);
    public const uint KeyUp=0x0002, Unicode=0x0004;
    private static readonly Dictionary<string,ushort> Keys=new(StringComparer.OrdinalIgnoreCase){
      ["ctrl"]=0x11,["control"]=0x11,["alt"]=0x12,["shift"]=0x10,["win"]=0x5B,
      ["enter"]=0x0D,["escape"]=0x1B,["esc"]=0x1B,["tab"]=0x09,["backspace"]=0x08,
      ["delete"]=0x2E,["space"]=0x20,["home"]=0x24,["end"]=0x23,
      ["up"]=0x26,["down"]=0x28,["left"]=0x25,["right"]=0x27,
      ["pageup"]=0x21,["pagedown"]=0x22,["insert"]=0x2D,
      ["f1"]=0x70,["f2"]=0x71,["f3"]=0x72,["f4"]=0x73,["f5"]=0x74,
      ["f6"]=0x75,["f7"]=0x76,["f8"]=0x77,["f9"]=0x78,["f10"]=0x79,
      ["f11"]=0x7A,["f12"]=0x7B
    };
    [StructLayout(LayoutKind.Sequential)] private struct KeyInput {
        public ushort VirtualKey,ScanCode;
        public uint Flags,Time;
        public nint Extra;
    }
    [StructLayout(LayoutKind.Sequential)] private struct MouseInput {
        public int X,Y;public uint Data,Flags,Time;public nint Extra;
    }
    [StructLayout(LayoutKind.Explicit)] private struct InputData {
        [FieldOffset(0)]public MouseInput Mouse;
        [FieldOffset(0)]public KeyInput Keyboard;
    }
    [StructLayout(LayoutKind.Sequential)] private struct Input {
        public uint Type;
        public InputData Data;
    }
    [DllImport("user32.dll",SetLastError=true)]
    private static extern uint SendInput(uint count,[In] Input[] inputs,int size);
    public static int InputSize => Marshal.SizeOf<Input>();
    public static int KeyboardSize => Marshal.SizeOf<KeyInput>();

    private static ushort KeyFor(string value) {
        value=value.Trim();
        if(Keys.TryGetValue(value,out var code))return code;
        if(value.Length==1) {
            char c=char.ToUpperInvariant(value[0]);
            if(c is >= 'A' and <= 'Z' or >= '0' and <= '9')return c;
        }
        throw new ArgumentException("Tecla nao reconhecida: "+value);
    }
    public static IReadOnlyList<KeyStroke> BuildShortcut(string shortcut) {
        if(string.IsNullOrWhiteSpace(shortcut)||shortcut.Length>64)
            throw new ArgumentException("Atalho invalido.");
        string[] parts=shortcut.Split('+',StringSplitOptions.TrimEntries|StringSplitOptions.RemoveEmptyEntries);
        if(parts.Length==0||parts.Length>4)throw new ArgumentException("Quantidade de teclas invalida.");
        var codes=parts.Select(KeyFor).ToArray();
        if(codes.Distinct().Count()!=codes.Length)throw new ArgumentException("Teclas repetidas.");
        List<KeyStroke> items=new();
        foreach(ushort key in codes)items.Add(new KeyStroke(key,0,0));
        foreach(ushort key in codes.Reverse())items.Add(new KeyStroke(key,0,KeyUp));
        return items;
    }
    public static IReadOnlyList<KeyStroke> BuildUnicode(string text) {
        if(text is null||text.Length>2000)throw new ArgumentException("Texto muito grande.");
        var items=new List<KeyStroke>(text.Length*2);
        foreach(char ch in text) {
            items.Add(new KeyStroke(0,ch,Unicode));
            items.Add(new KeyStroke(0,ch,Unicode|KeyUp));
        }
        return items;
    }
    private static void Inject(IReadOnlyList<KeyStroke> sequence) {
        if(!SessionStatus.Read().Available)throw new InvalidOperationException("Desktop Windows nao esta ativo.");
        if(sequence.Count==0)return;
        var native=sequence.Select(s=>new Input {
           Type=1,
           Data=new InputData { Keyboard=new KeyInput {
              VirtualKey=s.VirtualKey,ScanCode=s.ScanCode,Flags=s.Flags
           }}
        }).ToArray();
        // Enviar em lote preserva os pares e reduz latencia.
        uint sent=SendInput((uint)native.Length,native,InputSize);
        if(sent!=native.Length)throw new InvalidOperationException("O Windows recusou o teclado: "+Marshal.GetLastWin32Error());
    }
    public static void Shortcut(string value)=>Inject(BuildShortcut(value));
    public static void TypeText(string text)=>Inject(BuildUnicode(text));
}
