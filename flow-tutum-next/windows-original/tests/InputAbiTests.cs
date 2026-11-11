using FlowTutum.Next;
using System.Reflection;
using System.Runtime.InteropServices;
int checks=0;
void Check(bool ok,string text){if(!ok)throw new Exception("ERRO "+text);Console.WriteLine("OK "+text);checks++;}
var nested=typeof(InputEngine).GetNestedType("INPUT",BindingFlags.NonPublic);
var mouse=typeof(InputEngine).GetNestedType("MOUSEINPUT",BindingFlags.NonPublic);
Check(nested!=null && Marshal.SizeOf(nested)==40,"SendInput.INPUT possui 40 bytes x64");
Check(mouse!=null && Marshal.SizeOf(mouse)==32,"SendInput.MOUSEINPUT possui 32 bytes x64");
Check(IntPtr.Size==8,"Processo de entrada x64");
var monitors=InputEngine.Monitors();
Check(monitors.Length>0,"Pelo menos uma tela identificada");
foreach(var m in monitors){Check(m.IsValid,"Tela com dimensoes validas");}
Check(KeyboardEngine.InputSize==40,"SendInput teclado usa INPUT de 40 bytes");
Check(KeyboardEngine.KeyboardSize==24,"KEYBDINPUT usa 24 bytes x64");
var shortcut=KeyboardEngine.BuildShortcut("ctrl+c");
Check(shortcut.Count==4&&shortcut[0].VirtualKey==0x11&&shortcut[1].VirtualKey==0x43&&
    shortcut[2].Flags==KeyboardEngine.KeyUp&&shortcut[3].VirtualKey==0x11,"Ctrl+C DOWN/UP corretos");
var unicode=KeyboardEngine.BuildUnicode("ação");
Check(unicode.Count==8&&unicode[0].Flags==KeyboardEngine.Unicode&&
    unicode[1].Flags==(KeyboardEngine.Unicode|KeyboardEngine.KeyUp),"Texto Unicode em pares");
bool invalid=false;
try{KeyboardEngine.BuildShortcut("ctrl+naoexiste");}catch(ArgumentException){invalid=true;}
Check(invalid,"Atalho desconhecido recusado");
invalid=false;
try{KeyboardEngine.BuildShortcut("alt+alt");}catch(ArgumentException){invalid=true;}
Check(invalid,"Duplicacao de modificador recusada");
Console.WriteLine("INPUT_ABI_TESTS="+checks+" PASS");
