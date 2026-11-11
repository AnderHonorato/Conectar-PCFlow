using FlowTutum.Next;
using System.Globalization;
int tests=0;
void Check(bool result,string name) {if(!result)throw new Exception("FALHOU: "+name); Console.WriteLine("OK "+name);tests++;}
foreach(var locale in new[]{"pt-BR","en-US"}) {
    CultureInfo.CurrentCulture=CultureInfo.GetCultureInfo(locale);
    var q=Coordinates.FromNormalized(.25,.75,new MonitorGeometry(-1920,0,1920,1080));
    Check(q.X==-1440 && q.Y==809,"Monitor negativo "+locale);
    var p=Coordinates.FromClient(250,175,100,100,600,300);
    Check(Math.Abs(p.X-.25)<.000001 && Math.Abs(p.Y-.25)<.000001,"Mapeamento DOM "+locale);
}
var origin=Coordinates.FromNormalized(0,0,new MonitorGeometry(0,0,1920,1080));
var edge=Coordinates.FromNormalized(1,1,new MonitorGeometry(0,0,1920,1080));
Check(origin.X==0&&origin.Y==0&&edge.X==1919&&edge.Y==1079,"Limites pixel");
bool rejected=false;try {Coordinates.FromNormalized(double.NaN,.5,new MonitorGeometry(0,0,1920,1080));}catch(ArgumentOutOfRangeException){rejected=true;}
Check(rejected,"NaN recusado");
rejected=false;try{Coordinates.FromNormalized(1.1,.5,new MonitorGeometry(0,0,1920,1080));}catch(ArgumentOutOfRangeException){rejected=true;}
Check(rejected,"Fora da tela recusado");
foreach(var invalidSize in new[]{double.NaN,double.PositiveInfinity,double.NegativeInfinity,0.0,-1.0}) {
    rejected=false;
    try { Coordinates.FromClient(250,175,100,100,invalidSize,300); }
    catch(ArgumentException) { rejected=true; }
    Check(rejected,"Largura DOM invalida recusada: "+invalidSize);
    rejected=false;
    try { Coordinates.FromClient(250,175,100,100,600,invalidSize); }
    catch(ArgumentException) { rejected=true; }
    Check(rejected,"Altura DOM invalida recusada: "+invalidSize);
}
// Teclas multimídia devem gerar pares completos DOWN/UP sem modificadores presos.
foreach(var atalho in new[]{"midia-reproduzir","midia-parar","midia-anterior","midia-proxima","volume-mudo","volume-menos","volume-mais"}) {
    var sequencia=KeyboardEngine.BuildShortcut(atalho);
    Check(sequencia.Count==2 && sequencia[0].Flags==0 &&
          sequencia[1].Flags==KeyboardEngine.KeyUp &&
          sequencia[0].VirtualKey==sequencia[1].VirtualKey,
          "Multimidia com tecla liberada: "+atalho);
}
bool atalhoInvalido=false;
try {KeyboardEngine.BuildShortcut("midia-executar-comando");}
catch(ArgumentException){atalhoInvalido=true;}
Check(atalhoInvalido,"Comando de midia desconhecido recusado");
Console.WriteLine("FLOW_TUTUM_GEOMETRIA="+tests+" TESTES PASSARAM");
