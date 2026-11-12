using FlowTutum.Next;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;

const string version="2.0.0-alpha.10";
var port=int.TryParse(Environment.GetEnvironmentVariable("FLOW_TUTUM_NEXT_PORT"),out int selectedPort)
    &&selectedPort>=1024&&selectedPort<=65535?selectedPort:49900;
var appRoot=new[] {
    Path.Combine(AppContext.BaseDirectory,"web"),
    Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..","web")),
    Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..","..","..","web")),
    Path.GetFullPath(Path.Combine(AppContext.BaseDirectory,"..","..","..","..","web")),
    Path.Combine(Directory.GetCurrentDirectory(),"web")
}.FirstOrDefault(Directory.Exists) ?? throw new DirectoryNotFoundException("Pasta web nao encontrada.");
var ffmpegExe=Directory.Exists(Path.Combine(Path.GetDirectoryName(appRoot)!,"tools","ffmpeg"))
    ?Directory.GetFiles(Path.Combine(Path.GetDirectoryName(appRoot)!,"tools","ffmpeg"),"ffmpeg.exe",SearchOption.AllDirectories).FirstOrDefault()
    :null;
var store=new PairStore();
var input=new InputEngine();
var approval=new ConcurrentDictionary<string,DateTime>();
var requests=new ConcurrentDictionary<string,byte>();
// Apenas o WebSocket mais recente pode controlar e liberar o estado de cada dispositivo.
var inputOwners=new ConcurrentDictionary<string,Guid>();
var pairFailures=new ConcurrentDictionary<string,List<DateTime>>();
var videoSlot=new SemaphoreSlim(1,1);
var frame=new FrameService();
var pin=RandomNumberGenerator.GetInt32(100000,1000000).ToString();
var tailnetEnabled=Environment.GetEnvironmentVariable("FLOW_TUTUM_NEXT_TAILNET")=="1";
var tailscaleIp=NetworkInterface.GetAllNetworkInterfaces()
    .Where(n=>n.OperationalStatus==OperationalStatus.Up &&
      n.Name.Contains("Tailscale",StringComparison.OrdinalIgnoreCase))
    .SelectMany(n=>n.GetIPProperties().UnicastAddresses)
    .Select(x=>x.Address)
    .FirstOrDefault(x=>x.AddressFamily==AddressFamily.InterNetwork &&
      x.GetAddressBytes() is var b && b[0]==100 && b[1]>=64 && b[1]<=127);
var asWifiConfig=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
    "FlowTutumNext","as-rede-local.conf");
var persistentAsWifi=Environment.UserName.Equals("AS",StringComparison.OrdinalIgnoreCase)&&
    File.Exists(asWifiConfig) &&
    File.ReadAllText(asWifiConfig).Trim()=="AS_WIFI_192.168.1.3";
var localEnabled=Environment.GetEnvironmentVariable("FLOW_TUTUM_NEXT_LAN")=="1"||
    persistentAsWifi;
var lanAddresses=NetworkInterface.GetAllNetworkInterfaces()
  .Where(n=>n.OperationalStatus==OperationalStatus.Up &&
    n.NetworkInterfaceType is NetworkInterfaceType.Ethernet or NetworkInterfaceType.Wireless80211 &&
    !n.Name.Contains("Tailscale",StringComparison.OrdinalIgnoreCase))
  .SelectMany(n=>n.GetIPProperties().UnicastAddresses)
  .Where(n=>n.Address.AddressFamily==AddressFamily.InterNetwork&&n.IPv4Mask!=null &&
    IsPrivateAddress(n.Address))
  .ToArray();
var localUnlockEnabled=localEnabled &&
    (Environment.GetEnvironmentVariable("FLOW_TUTUM_NEXT_AUTO_LAN")=="1"||
      persistentAsWifi) &&
    lanAddresses.Any(n=>n.Address.ToString()=="192.168.1.3" && n.IPv4Mask.ToString()=="255.255.255.0");
LocalAccessSettings.Configure(localUnlockEnabled);
var urls=new List<string>{"http://127.0.0.1:"+port};
if(tailnetEnabled&&tailscaleIp is not null)urls.Add("http://"+tailscaleIp+":"+port);
if(localEnabled)foreach(var entry in lanAddresses)urls.Add("http://"+entry.Address+":"+port);
var builder=WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls(urls.ToArray());
var app=builder.Build();
app.UseWebSockets(new WebSocketOptions{KeepAliveInterval=TimeSpan.FromSeconds(15)});
app.Use(async (ctx,next)=> {
    ctx.Response.Headers.CacheControl="no-store";
    var remote=ctx.Connection.RemoteIpAddress??IPAddress.None;
    var local=ctx.Connection.LocalIpAddress??IPAddress.None;
    bool viaTailnet=tailnetEnabled&&tailscaleIp is not null&&local.Equals(tailscaleIp)&&
        remote.AddressFamily==AddressFamily.InterNetwork &&
        remote.GetAddressBytes() is var rb && rb[0]==100 && rb[1]>=64 && rb[1]<=127;
    bool viaLan=localEnabled && lanAddresses.Any(n=>local.Equals(n.Address)&&InSubnet(remote,n.Address,n.IPv4Mask));
    if(!IPAddress.IsLoopback(remote)&&!viaTailnet&&!viaLan){
        ctx.Response.StatusCode=403;return;
    }
    await next();
});
app.MapGet("/",()=>Results.File(Path.Combine(appRoot,"index.html"),"text/html; charset=utf-8"));
app.MapGet("/app.css",()=>Results.File(Path.Combine(appRoot,"app.css"),"text/css; charset=utf-8"));
app.MapGet("/app.js",()=>Results.File(Path.Combine(appRoot,"app.js"),"application/javascript; charset=utf-8"));
app.MapGet("/enhancements.js",()=>Results.File(Path.Combine(appRoot,"enhancements.js"),"application/javascript; charset=utf-8"));
// Arquivos da interface são publicados por lista fechada: nada de servir a pasta inteira.
// Sem estas rotas, os módulos de toque e suas folhas de estilo retornavam 404.
var recursosInterface=new [] {
    "navegacao-rapida.js","navegacao-rapida.css",
    "feedback-gestos.js","feedback-gestos.css",
    "bloqueio-toque.js","bloqueio-toque.css",
    "perfis-uso.js","perfis-uso.css",
    "politica-visualizacao.js","modo-visualizacao.js","modo-visualizacao.css",
    "gestos-configuraveis.mjs","gestos-configuraveis.css",
    "mapa-navegacao.js","mapa-navegacao.mjs","mapa-navegacao.css",
    "diagnostico-sessao.js","diagnostico-sessao.css",
    "guia-gestos.js","guia-gestos.css",
    "controle-midia.js","controle-midia.css",
    "apresentacao.js","apresentacao.css"
};
foreach(var recurso in recursosInterface) {
    var arquivo=recurso;
    var tipo=arquivo.EndsWith(".css",StringComparison.OrdinalIgnoreCase)
        ?"text/css; charset=utf-8":"application/javascript; charset=utf-8";
    app.MapGet("/"+arquivo,()=>Results.File(Path.Combine(appRoot,arquivo),tipo));
}

app.MapGet("/FlowTutum.apk",()=>{
    var file=Path.Combine(appRoot,"FlowTutum.apk");
    return File.Exists(file)?Results.File(file,
        "application/vnd.android.package-archive","FlowTutum-Next.apk"):
        Results.NotFound();
});
app.MapGet("/api/health",()=>Results.Json(new {app="Flow Tutum Next",version,ready=true,
    session=SessionStatus.Read(),network=localEnabled&&lanAddresses.Length>0?"lan-private":
        tailnetEnabled&&tailscaleIp is not null?"tailscale-private":"loopback-only",
    video=ffmpegExe is null?"jpeg-fallback":"h264-fmp4",
    localDirect=LocalAccessSettings.Enabled}));
app.MapGet("/api/session-availability",()=>Results.Json(SessionStatus.Read()));
app.MapPost("/api/local-connect",(HttpContext ctx)=> {
    var remote=ctx.Connection.RemoteIpAddress??IPAddress.None;
    var local=ctx.Connection.LocalIpAddress??IPAddress.None;
    bool home=LocalAccessSettings.Enabled &&
        ((IPAddress.IsLoopback(remote)&&IPAddress.IsLoopback(local))||
         lanAddresses.Any(n=>n.Address.ToString()=="192.168.1.3" &&
           local.Equals(n.Address)&&InSubnet(remote,n.Address,n.IPv4Mask)));
    if(!home)return Results.StatusCode(403);
    var origin=ctx.Request.Headers.Origin.ToString();
    if(!string.IsNullOrEmpty(origin) &&
       !string.Equals(origin,ctx.Request.Scheme+"://"+ctx.Request.Host,
           StringComparison.OrdinalIgnoreCase))return Results.StatusCode(403);
    var token=store.CreateLocal(remote);
    if(token is null)return Results.StatusCode(429);
    return Results.Json(new{token,device=Environment.MachineName,mode="local-direto",
        expiresMinutes=60});
});
app.MapPost("/api/pair",async (HttpContext ctx)=> {
    var address=ctx.Connection.RemoteIpAddress?.ToString()??"unknown";
    var failures=pairFailures.GetOrAdd(address,_=>new List<DateTime>());
    lock(failures){
        failures.RemoveAll(at=>at<DateTime.UtcNow.AddMinutes(-10));
        if(failures.Count>=6)return Results.StatusCode(429);
    }
    // Pareamento e um endpoint nao autenticado: validar formato antes de ler o corpo.
    // Evita erro 500 para Content-Type ausente/incorreto e nao aceita outros formatos.
    if(!ctx.Request.HasJsonContentType())return Results.StatusCode(415);
    PairInput? info;
    try {
        if(ctx.Request.ContentLength is > 4096)return Results.StatusCode(413);
        var sizeFeature=ctx.Features.Get<Microsoft.AspNetCore.Http.Features.IHttpMaxRequestBodySizeFeature>();
        if(sizeFeature is {IsReadOnly:false})sizeFeature.MaxRequestBodySize=4096;
        info=await ctx.Request.ReadFromJsonAsync<PairInput>();
    }catch(JsonException){return Results.BadRequest(new{error="JSON de pareamento invalido."});}
    catch(BadHttpRequestException ex) when(ex.StatusCode==413){return Results.StatusCode(413);}
    catch(InvalidDataException){return Results.StatusCode(413);}
    if(info is null||info.Pin!=pin){
        lock(failures)failures.Add(DateTime.UtcNow);
        return Results.Unauthorized();
    }
    lock(failures)failures.Clear();
    var name=(info.Name??"Meu dispositivo").Trim();
    if(name.Length>40)name=name[..40];
    var token=store.Create(name);
    return Results.Json(new{token,device=Environment.MachineName});
});
app.MapGet("/api/info",(HttpContext ctx)=> {
    var peer=store.Auth(ctx);
    if(peer is null)return Results.Unauthorized();
    return Results.Json(new{
        version,device=Environment.MachineName,name=peer.Name,
        session=SessionStatus.Read(),
        network=localEnabled&&lanAddresses.Length>0
           ?"Rede local • "+string.Join(", ",lanAddresses.Select(x=>x.Address.ToString()))+":"+port
           :tailnetEnabled&&tailscaleIp is not null
            ?"Tailscale privado • "+tailscaleIp+": "+port
            :"Somente neste computador (versao de testes)",
        monitors=InputEngine.Monitors().Select((m,i)=>new{index=i,left=m.Left,top=m.Top,width=m.Width,height=m.Height}),
        control=(peer.Local&&LocalAccessSettings.Enabled) || (approval.TryGetValue(peer.Hash,out var time)&&time>DateTime.UtcNow),
        localDirect=LocalAccessSettings.Enabled,
        capabilities=new{pointer=true,keyboard=true,files=false,codec=ffmpegExe is null?"jpeg-fallback":"h264-fmp4",websocket=true}
    });
});
app.MapGet("/api/frame",(HttpContext ctx,int? monitor,int? width)=> {
    if(store.Auth(ctx) is null)return Results.Unauthorized();
    var desktop=SessionStatus.Read();
    if(!desktop.Available)return Results.Json(new{error=desktop.Message,session=desktop},statusCode:409);
    try{return Results.File(frame.Snapshot(monitor??0,Math.Clamp(width??960,480,1920)),"image/jpeg");}
    catch(Exception e){return Results.Json(new{error=e.Message},statusCode:503);}
});
app.MapGet("/api/video",async (HttpContext ctx,int? monitor,int? width,int? fps)=> {
    if(store.Auth(ctx) is null){ctx.Response.StatusCode=401;return;}
    var desktop=SessionStatus.Read();
    if(!desktop.Available){ctx.Response.StatusCode=409;await ctx.Response.WriteAsJsonAsync(new{error=desktop.Message,session=desktop});return;}
    if(ffmpegExe is null){ctx.Response.StatusCode=503;return;}
    if(!await videoSlot.WaitAsync(0)){ctx.Response.StatusCode=429;return;}
    try{
    var screens=System.Windows.Forms.Screen.AllScreens;
    int n=monitor??0;
    if(n<0||n>=screens.Length){ctx.Response.StatusCode=400;return;}
    int size=Math.Clamp(width??960,480,1280);
    int hz=Math.Clamp(fps??12,5,20);
    var area=screens[n].Bounds;
    var arguments=new []{"-hide_banner","-loglevel","error","-nostdin",
        "-f","gdigrab","-draw_mouse","1","-framerate",hz.ToString(),
        "-offset_x",area.Left.ToString(),"-offset_y",area.Top.ToString(),
        "-video_size",area.Width+"x"+area.Height,"-i","desktop",
        "-vf","scale="+size+":-2:flags=fast_bilinear",
        "-c:v","libx264","-preset","ultrafast","-tune","zerolatency",
        "-pix_fmt","yuv420p","-profile:v","baseline",
        "-g",hz.ToString(),"-keyint_min",hz.ToString(),"-sc_threshold","0",
        "-crf","29","-maxrate","1400k","-bufsize","350k",
        "-movflags","+frag_keyframe+empty_moov+default_base_moof+omit_tfhd_offset",
        "-frag_duration","250000","-f","mp4","pipe:1"};
    var start=new ProcessStartInfo(ffmpegExe){UseShellExecute=false,CreateNoWindow=true,
        RedirectStandardOutput=true,RedirectStandardError=true};
    foreach(var arg in arguments)start.ArgumentList.Add(arg);
    using var encoder=new Process{StartInfo=start};
    if(!encoder.Start()){ctx.Response.StatusCode=503;return;}
    var errors=encoder.StandardError.ReadToEndAsync();
    try{
        ctx.Response.ContentType="video/mp4";
        ctx.Response.Headers["X-Content-Type-Options"]="nosniff";
        await encoder.StandardOutput.BaseStream.CopyToAsync(ctx.Response.Body,65536,ctx.RequestAborted);
    }catch(OperationCanceledException){}catch(IOException){}
    finally{
        if(!encoder.HasExited)encoder.Kill(entireProcessTree:true);
        var errorText=await errors;
        if(errorText.Length>0)Console.WriteLine("H264: "+errorText[..Math.Min(400,errorText.Length)]);
    }
    }finally{videoSlot.Release();}
});
app.MapPost("/api/unpair",(HttpContext ctx)=> {
    var peer=store.Auth(ctx);
    if(peer is null)return Results.Unauthorized();
    approval.TryRemove(peer.Hash,out _);
    try{input.Release(peer.Hash);}catch(Exception ex){Console.WriteLine("Unpair release: "+ex.Message);}
    store.Revoke(peer.Hash);
    return Results.Ok(new{revoked=true});
});
app.MapGet("/api/cursor",(HttpContext ctx)=> {
    if(store.Auth(ctx) is null)return Results.Unauthorized();
    var desktop=SessionStatus.Read();
    if(!desktop.Available)return Results.Json(new {error=desktop.Message,session=desktop},statusCode:409);
    return Results.Json(new{cursor=InputEngine.CursorLocation()});
});
app.MapGet("/api/control/status",(HttpContext ctx)=> {
    var peer=store.Auth(ctx);
    if(peer is null)return Results.Unauthorized();
    var desktop=SessionStatus.Read();
    return Results.Json(new{approved=peer.Local||(approval.TryGetValue(peer.Hash,out var time)&&time>DateTime.UtcNow),
      available=desktop.Available,session=desktop,localDirect=peer.Local});
});
app.MapPost("/api/control/request",(HttpContext ctx)=> {
    var peer=store.Auth(ctx);
    if(peer is null)return Results.Unauthorized();
    var desktop=SessionStatus.Read();
    if(!desktop.Available)return Results.Json(new{error=desktop.Message,session=desktop},statusCode:409);
    if(peer.Local&&LocalAccessSettings.Enabled)return Results.Ok(new{approved=true,localDirect=true});
    if(!requests.TryAdd(peer.Hash,0))return Results.Json(new{pending=true});
    var hash=peer.Hash;var name=peer.Name;
    var thread=new Thread(()=> {
        try{
            var yes=MessageBox.Show("Permitir CONTROLE REMOTO por 15 minutos para: "+name+
                "?\n\nSomente autorize se reconhecer o dispositivo.","Flow Tutum 2.0 - Autorizar controle",
                MessageBoxButtons.YesNo,MessageBoxIcon.Question,MessageBoxDefaultButton.Button2);
            if(yes==DialogResult.Yes)approval[hash]=DateTime.UtcNow.AddMinutes(15);
        }finally{requests.TryRemove(hash,out _);}
    });
    thread.IsBackground=true;thread.SetApartmentState(ApartmentState.STA);thread.Start();
    return Results.Json(new{pending=true});
});
app.Map("/ws/input",async (HttpContext ctx)=> {
    if(!ctx.WebSockets.IsWebSocketRequest){ctx.Response.StatusCode=400;return;}
    // Browsers always send Origin. Block cross-site WebSocket hijacking before upgrade.
    var origin=ctx.Request.Headers.Origin.ToString();
    if(!string.IsNullOrEmpty(origin) &&
       !string.Equals(origin,ctx.Request.Scheme+"://"+ctx.Request.Host,
           StringComparison.OrdinalIgnoreCase)){
        ctx.Response.StatusCode=403;return;
    }
    using var socket=await ctx.WebSockets.AcceptWebSocketAsync();
    var bytes=new byte[2048];
    string? id=null;
    var connectionId=Guid.NewGuid();
    long lastSeq=0;
    // Um cliente que nao autentica nao pode manter um socket aberto indefinidamente.
    using var authDeadline=CancellationTokenSource.CreateLinkedTokenSource(ctx.RequestAborted);
    authDeadline.CancelAfter(TimeSpan.FromSeconds(10));
    try {
        while(socket.State==WebSocketState.Open) {
            using var m=new MemoryStream();WebSocketReceiveResult result;
            do {
                result=await socket.ReceiveAsync(new ArraySegment<byte>(bytes),
                    id is null?authDeadline.Token:ctx.RequestAborted);
                if(result.MessageType==WebSocketMessageType.Close)return;
                if(result.MessageType!=WebSocketMessageType.Text){
                    await socket.CloseAsync(WebSocketCloseStatus.InvalidMessageType,
                        "Somente texto JSON",CancellationToken.None);
                    return;
                }
                if(m.Length+result.Count>8192){
                    await socket.CloseAsync(WebSocketCloseStatus.MessageTooBig,
                        "Pacote excede 8192 bytes",CancellationToken.None);
                    return;
                }
                m.Write(bytes,0,result.Count);
            }while(!result.EndOfMessage);
            InputPacket? cmd;
            try {
                cmd=JsonSerializer.Deserialize<InputPacket>(m.ToArray(),
                    new JsonSerializerOptions{PropertyNameCaseInsensitive=true});
            }catch(JsonException){
                await socket.CloseAsync(WebSocketCloseStatus.InvalidPayloadData,
                    "JSON invalido",CancellationToken.None);
                return;
            }
            if(cmd is null)continue;
            string response;
            if(id is null) {
                var peer=store.Auth(cmd.Token,ctx.Connection.RemoteIpAddress);
                if(peer is null){await socket.CloseAsync(WebSocketCloseStatus.PolicyViolation,"Nao autorizado",CancellationToken.None);return;}
                id=peer.Hash;
                inputOwners[id]=connectionId;
                input.Release(id); // evita arrasto/tecla presa da conexao substituida
                response="{\"ready\":true}";
            }else{
                try {
                    if(!inputOwners.TryGetValue(id,out var owner)||owner!=connectionId) {
                        await socket.CloseAsync(WebSocketCloseStatus.PolicyViolation,
                            "Conexao substituida",CancellationToken.None);
                        return;
                    }
                    var desktop=SessionStatus.Read();
                    if(!desktop.Available)throw new InvalidOperationException(desktop.Message);
                    if(!store.IsActiveHash(id))throw new UnauthorizedAccessException("Dispositivo desvinculado.");
                    if(!store.IsAutoLocalHash(id,ctx.Connection.RemoteIpAddress) &&
                       (!approval.TryGetValue(id,out var until)||until<DateTime.UtcNow))
                        throw new UnauthorizedAccessException("Controle nao autorizado no PC.");
                    if(cmd.Seq<=lastSeq)throw new ArgumentException("Sequencia duplicada.");
                    lastSeq=cmd.Seq;
                    if(cmd.Phase=="text")KeyboardEngine.TypeText(cmd.Text??"");
                    else if(cmd.Phase=="key")KeyboardEngine.Shortcut(cmd.Keys??"");
                    else input.Apply(id,cmd.Phase??"",cmd.Screen,cmd.X,cmd.Y,cmd.Button??"left",cmd.Wheel);
                    response=JsonSerializer.Serialize(new {ok=true,seq=lastSeq,cursor=InputEngine.CursorLocation()});
                }catch(Exception ex){
                    if(inputOwners.TryGetValue(id,out var owner)&&owner==connectionId)
                        try{input.Release(id);}catch(Exception releaseError){Console.WriteLine("Release: "+releaseError.Message);}
                    response=JsonSerializer.Serialize(new {ok=false,seq=cmd.Seq,error=ex.Message});
                }
            }
            await socket.SendAsync(Encoding.UTF8.GetBytes(response),WebSocketMessageType.Text,true,ctx.RequestAborted);
        }
    }catch(OperationCanceledException){}
    catch(WebSocketException){}
    finally{
        if(id!=null && inputOwners.TryRemove(new KeyValuePair<string,Guid>(id,connectionId)))
            try{input.Release(id);}catch(Exception releaseError){Console.WriteLine("Release final: "+releaseError.Message);}
    }
});
app.Lifetime.ApplicationStarted.Register(()=>{
    Console.WriteLine("Flow Tutum NEXT "+version+
        " em http://127.0.0.1:"+port+"/\nCodigo temporario de pareamento: "+pin+
        "\nAcesso: "+(tailnetEnabled&&tailscaleIp is not null?
           "Tailscale privado em "+tailscaleIp+":"+port:
           "somente localhost")+".");
    TrayHost.Start(pin,port,()=>app.Lifetime.StopApplication(),()=>{
        store.RevokeAllLocal();
        input.ReleaseAll();
    });
});
Console.Title="Flow Tutum 2.0 alpha - teste isolado";
await app.RunAsync();

static bool IsPrivateAddress(IPAddress address) {
  var b=address.GetAddressBytes();
  return b.Length==4&&(b[0]==10 || (b[0]==172&&b[1]>=16&&b[1]<=31) ||
     (b[0]==192&&b[1]==168));
}
static bool InSubnet(IPAddress remote,IPAddress local,IPAddress mask) {
  var r=remote.MapToIPv4().GetAddressBytes();
  var l=local.GetAddressBytes();var m=mask.GetAddressBytes();
  return r.Length==4 && l.Length==4 && m.Length==4 &&
     Enumerable.Range(0,4).All(i=>(r[i]&m[i])==(l[i]&m[i]));
}
record PairInput(string Pin,string? Name);
record InputPacket(string? Token,long Seq,string? Phase,int Screen,double X,double Y,string? Button,int Wheel,
    string? Text,string? Keys);
record PairEntry(string Hash,string Name,bool Local=false,string? Ip=null,DateTime ExpiresUtc=default);
sealed class PairStore {
    private readonly string file=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"FlowTutumNext","pareamentos.json");
    private readonly object sync=new();
    private readonly ConcurrentDictionary<string,PairEntry> pairs=new();
    private readonly ConcurrentDictionary<string,PairEntry> ephemeral=new();
    public PairStore(){
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        if(File.Exists(file))try{
            foreach(var p in JsonSerializer.Deserialize<PairEntry[]>(File.ReadAllText(file))??[])
                if(!string.IsNullOrWhiteSpace(p.Hash))pairs.TryAdd(p.Hash,p);
        }catch(Exception){Console.WriteLine("Nao foi possivel ler pareamentos anteriores.");}
    }
    static string Hash(string token)=>Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
    private void Persist() {
        var temporary=file+"."+Path.GetRandomFileName()+".tmp";
        try{
            File.WriteAllText(temporary,JsonSerializer.Serialize(pairs.Values.ToArray()));
            File.Move(temporary,file,overwrite:true);
        } finally{if(File.Exists(temporary))File.Delete(temporary);}
    }
    public string Create(string name){
        var token=Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        var hash=Hash(token);
        lock(sync){
            pairs.TryAdd(hash,new PairEntry(hash,name));
            try{Persist();}catch{pairs.TryRemove(hash,out _);throw;}
        }
        return token;
    }
    public string? CreateLocal(IPAddress address) {
        lock(sync) {
            var now=DateTime.UtcNow;
            foreach(var expired in ephemeral.Where(p=>p.Value.ExpiresUtc<=now))
                ephemeral.TryRemove(expired.Key,out _);
            // Limites atomicos: impedem que requisicoes paralelas esgotem a memoria.
            if(ephemeral.Count>=256 || ephemeral.Values.Count(p=>p.Ip==address.ToString())>=8)
                return null;
            var token=Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
            var hash=Hash(token);
            ephemeral[hash]=new PairEntry(hash,"Aparelho no Wi-Fi",true,
                address.ToString(),now.AddHours(1));
            return token;
        }
    }
    public void RevokeAllLocal()=>ephemeral.Clear();
    public bool IsActiveHash(string hash)=>pairs.ContainsKey(hash) ||
        (LocalAccessSettings.Enabled && ephemeral.TryGetValue(hash,out var p) && p.ExpiresUtc>DateTime.UtcNow);
    public bool IsAutoLocalHash(string hash,IPAddress? address)=>
        LocalAccessSettings.Enabled && ephemeral.TryGetValue(hash,out var p) && p.ExpiresUtc>DateTime.UtcNow &&
        address is not null && p.Ip==address.ToString();
    public bool Revoke(string hash){
        if(ephemeral.TryRemove(hash,out _))return true;
        lock(sync){
            bool removed=pairs.TryRemove(hash,out _);
            if(removed)Persist();
            return removed;
        }
    }
    public PairEntry? Auth(HttpContext ctx) {
        var value=ctx.Request.Headers.Authorization.ToString();
        return value.StartsWith("Bearer ",StringComparison.OrdinalIgnoreCase)?
            Auth(value[7..],ctx.Connection.RemoteIpAddress):null;
    }
    public PairEntry? Auth(string? token,IPAddress? address=null) {
        if(string.IsNullOrWhiteSpace(token)||token.Length!=64)return null;
        var hash=Hash(token);
        if(pairs.TryGetValue(hash,out var peer))return peer;
        if(LocalAccessSettings.Enabled && address is not null && ephemeral.TryGetValue(hash,out var local) &&
            local.ExpiresUtc>DateTime.UtcNow && local.Ip==address.ToString())return local;
        return null;
    }
}
sealed class FrameService {
    private readonly object gate=new();
    private byte[]? cached; private int cachedScreen=-1,cachedWidth;private DateTime at;
    public byte[] Snapshot(int monitor,int width){
        lock(gate) {
            if(cached!=null&&cachedScreen==monitor&&cachedWidth==width&&(DateTime.UtcNow-at).TotalMilliseconds<130)return cached;
            var screens=Screen.AllScreens;
            if(monitor<0||monitor>=screens.Length)throw new ArgumentException("Monitor invalido.");
            var bounds=screens[monitor].Bounds;
            int w=Math.Min(bounds.Width,width),h=Math.Max(1,(int)Math.Round(bounds.Height*(double)w/bounds.Width));
            using var source=new Bitmap(bounds.Width,bounds.Height);
            using(var g=Graphics.FromImage(source)){g.CopyFromScreen(bounds.Left,bounds.Top,0,0,bounds.Size);}
            using var scaled=new Bitmap(w,h);
            using(var g=Graphics.FromImage(scaled)){g.InterpolationMode=System.Drawing.Drawing2D.InterpolationMode.HighQualityBilinear;g.DrawImage(source,0,0,w,h);}
            using var output=new MemoryStream();
            var enc=ImageCodecInfo.GetImageEncoders().First(e=>e.MimeType=="image/jpeg");
            using var p=new EncoderParameters(1);p.Param[0]=new EncoderParameter(System.Drawing.Imaging.Encoder.Quality,68L);
            scaled.Save(output,enc,p);
            cached=output.ToArray();cachedScreen=monitor;cachedWidth=width;at=DateTime.UtcNow;return cached;
        }
    }
}
