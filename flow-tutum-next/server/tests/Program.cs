using System.Net;
using FlowTutum.Next.Security;

int executados = 0;
void Testar(string nome, Action acao)
{
    acao();
    executados++;
    Console.WriteLine("[PASSOU] " + nome);
}
void Exigir(bool condicao, string mensagem = "Condição não satisfeita")
{
    if (!condicao) throw new InvalidOperationException(mensagem);
}

var politica = new PoliticaRedeLocal(IPAddress.Parse("192.168.1.3"), IPAddress.Parse("255.255.255.0"));
var servidor = IPAddress.Parse("192.168.1.3");
var agora = new DateTimeOffset(2026, 11, 9, 20, 0, 0, TimeSpan.Zero);
var celular = IPAddress.Parse("192.168.1.20");

Testar("PC e celular na mesma LAN são reconhecidos", () =>
    Exigir(politica.EhPermitido(celular, servidor)));
Testar("IP mapeado em IPv6 é normalizado", () =>
    Exigir(politica.EhPermitido(IPAddress.Parse("::ffff:192.168.1.20"),
        IPAddress.Parse("::ffff:192.168.1.3"))));
Testar("rede vizinha é rejeitada", () =>
    Exigir(!politica.EhPermitido(IPAddress.Parse("192.168.2.20"), servidor)));
Testar("endereço público é rejeitado", () =>
    Exigir(!politica.EhPermitido(IPAddress.Parse("8.8.8.8"), servidor)));
Testar("requisição em interface inesperada é rejeitada", () =>
    Exigir(!politica.EhPermitido(celular, IPAddress.Parse("127.0.0.1"))));
Testar("endereço broadcast é rejeitado", () =>
    Exigir(!politica.EhPermitido(IPAddress.Parse("192.168.1.255"), servidor)));
Testar("endereço de rede é rejeitado", () =>
    Exigir(!politica.EhPermitido(IPAddress.Parse("192.168.1.0"), servidor)));
Testar("máscara inválida não inicia serviço", () =>
{
    try { _ = new PoliticaRedeLocal(servidor, IPAddress.Parse("255.0.255.0")); }
    catch (ArgumentException) { return; }
    throw new Exception("Aceitou máscara não contígua");
});
Testar("origem idêntica do painel é aceita", () =>
    Exigir(PoliticaRedeLocal.OrigemPermitida("http://192.168.1.3:49900",
        "http", "192.168.1.3:49900")));
Testar("origem externa é rejeitada", () =>
    Exigir(!PoliticaRedeLocal.OrigemPermitida("https://exemplo.invalido",
        "http", "192.168.1.3:49900")));
Testar("origem em outra porta é rejeitada", () =>
    Exigir(!PoliticaRedeLocal.OrigemPermitida("http://192.168.1.3:49901",
        "http", "192.168.1.3:49900")));
Testar("origem malformada é rejeitada", () =>
    Exigir(!PoliticaRedeLocal.OrigemPermitida("http://192.168.1.3:49900.evil.test",
        "http", "192.168.1.3:49900")));

var sessoes = new SessoesRedeLocal(TimeSpan.FromMinutes(30), ativo: true);
string token = sessoes.Emitir(celular, agora) ?? throw new Exception("Token não emitido");
Testar("token usa 32 bytes aleatórios", () => Exigir(token.Length == 64));
Testar("sessões recusam emissão para IP público, loopback e IPv6", () =>
{
    Exigir(sessoes.Emitir(IPAddress.Parse("8.8.8.8"), agora) is null);
    Exigir(sessoes.Emitir(IPAddress.Loopback, agora) is null);
    Exigir(sessoes.Emitir(IPAddress.IPv6Loopback, agora) is null);
});
Testar("sessões aceitam IPv4 privado mapeado em IPv6", () =>
{
    var mapped = IPAddress.Parse("::ffff:192.168.1.20");
    var temporario = sessoes.Emitir(mapped, agora);
    Exigir(temporario is not null);
    Exigir(sessoes.Verificar(temporario, celular, agora));
    Exigir(!sessoes.Verificar(temporario, IPAddress.Parse("8.8.8.8"), agora));
});

Testar("revogação individual mantém a outra sessão ativa", () =>
{
    var temporarias = new SessoesRedeLocal(TimeSpan.FromMinutes(10), ativo: true);
    var primeiro = temporarias.Emitir(celular, agora)!;
    var segundo = temporarias.Emitir(IPAddress.Parse("192.168.1.21"), agora)!;
    Exigir(temporarias.Revogar(primeiro));
    Exigir(!temporarias.Verificar(primeiro, celular, agora));
    Exigir(temporarias.Verificar(segundo, IPAddress.Parse("192.168.1.21"), agora));
    Exigir(temporarias.QuantidadeDeSessoes == 1);
    Exigir(!temporarias.Revogar(primeiro));
    Exigir(!temporarias.Revogar("invalido"));
    Exigir(!temporarias.Revogar(null));
});
Testar("revogação de token mapeado funciona entre IPv4 e IPv6", () =>
{
    var temporarias = new SessoesRedeLocal(ativo: true);
    var ipMapeado = IPAddress.Parse("::ffff:192.168.1.20");
    var t = temporarias.Emitir(ipMapeado, agora)!;
    Exigir(temporarias.Verificar(t, celular, agora));
    Exigir(temporarias.Revogar(t));
    Exigir(!temporarias.Verificar(t, ipMapeado, agora));
});

Testar("token válido apenas no IP emissor", () =>
{
    Exigir(sessoes.Verificar(token, celular, agora));
    Exigir(!sessoes.Verificar(token, IPAddress.Parse("192.168.1.21"), agora));
});
Testar("token inválido não concede controle", () =>
{
    Exigir(!sessoes.Verificar("abc", celular, agora));
    Exigir(!sessoes.Verificar(null, celular, agora));
});
Testar("token expira em 30 minutos", () =>
{
    Exigir(sessoes.Verificar(token, celular, agora.AddMinutes(29)));
    Exigir(!sessoes.Verificar(token, celular, agora.AddMinutes(30)));
});
Testar("verificação de token expirado remove sessão sem afetar outra", () =>
{
    var temporarias = new SessoesRedeLocal(TimeSpan.FromMinutes(1), ativo: true);
    var expirado = temporarias.Emitir(celular, agora)!;
    var vigente = temporarias.Emitir(celular, agora.AddSeconds(30))!;
    Exigir(temporarias.QuantidadeDeSessoes == 2);
    Exigir(!temporarias.Verificar(expirado, celular, agora.AddMinutes(1)));
    Exigir(temporarias.QuantidadeDeSessoes == 1);
    Exigir(temporarias.Verificar(vigente, celular, agora.AddMinutes(1)));
    Exigir(!temporarias.Revogar(expirado));
});
Testar("desligar Wi-Fi direto revoga todos os tokens", () =>
{
    sessoes.DefinirAtivo(false);
    Exigir(!sessoes.Verificar(token, celular, agora.AddMinutes(1)));
    Exigir(sessoes.QuantidadeDeSessoes == 0);
    Exigir(sessoes.Emitir(celular, agora.AddMinutes(1)) is null);
});
Testar("reativar não ressuscita tokens anteriores", () =>
{
    sessoes.DefinirAtivo(true);
    Exigir(!sessoes.Verificar(token, celular, agora.AddMinutes(1)));
    var novo = sessoes.Emitir(celular, agora.AddMinutes(1));
    Exigir(novo is not null && novo != token);
});

var limitador = new LimitadorDeConexoes(maximo: 3, janela: TimeSpan.FromMinutes(1));
Testar("limita tentativas de um endereço", () =>
{
    Exigir(limitador.Permitir(celular, agora));
    Exigir(limitador.Permitir(celular, agora));
    Exigir(limitador.Permitir(celular, agora));
    Exigir(!limitador.Permitir(celular, agora));
});
Testar("outra máquina tem limite independente", () =>
    Exigir(limitador.Permitir(IPAddress.Parse("192.168.1.21"), agora)));
Testar("limite libera após janela", () =>
    Exigir(limitador.Permitir(celular, agora.AddMinutes(1))));

var autenticacao = new ServicoDeAcessoLocal(
    politica, new SessoesRedeLocal(ativo: true),
    new LimitadorDeConexoes(maximo: 2));

Testar("serviço entrega token somente na rede certa", () =>
{
    var resultado = autenticacao.Conectar(celular, servidor,
        "http://192.168.1.3:49900", "http", "192.168.1.3:49900", agora);
    Exigir(resultado.CodigoHttp == 200 && resultado.Token?.Length == 64);
    Exigir(autenticacao.PodeControlar(resultado.Token, celular, servidor, agora));
});
Testar("serviço nega conexão externa e origem maliciosa", () =>
{
    var fora = autenticacao.Conectar(IPAddress.Parse("8.8.8.8"), servidor,
        null, "http", "192.168.1.3:49900", agora);
    var origem = autenticacao.Conectar(celular, servidor,
        "https://site-externo.test", "http", "192.168.1.3:49900", agora);
    Exigir(fora.CodigoHttp == 403 && origem.CodigoHttp == 403);
});
Testar("serviço limita criação de tokens por minuto", () =>
{
    var segundo = autenticacao.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora);
    var excedeu = autenticacao.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora);
    Exigir(segundo.CodigoHttp == 200 && excedeu.CodigoHttp == 429);
});
Testar("desconexão revoga apenas token do IP e interface autorizados", () =>
{
    var isolado = new ServicoDeAcessoLocal(politica,
        new SessoesRedeLocal(ativo: true), new LimitadorDeConexoes());
    var outroIp = IPAddress.Parse("192.168.1.21");
    var primeira = isolado.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora).Token!;
    var segunda = isolado.Conectar(outroIp, servidor, null,
        "http", "192.168.1.3:49900", agora).Token!;
    Exigir(!isolado.Desconectar(primeira, outroIp, servidor, agora));
    Exigir(!isolado.Desconectar(primeira, celular, IPAddress.Loopback, agora));
    Exigir(!isolado.Desconectar("invalido", celular, servidor, agora));
    Exigir(isolado.PodeControlar(primeira, celular, servidor, agora));
    Exigir(isolado.Desconectar(primeira, celular, servidor, agora));
    Exigir(!isolado.Desconectar(primeira, celular, servidor, agora));
    Exigir(!isolado.PodeControlar(primeira, celular, servidor, agora));
    Exigir(isolado.PodeControlar(segunda, outroIp, servidor, agora));
});
Testar("sessão expirada não pode ser desconectada nem reutilizada", () =>
{
    var isolado = new ServicoDeAcessoLocal(politica,
        new SessoesRedeLocal(TimeSpan.FromMinutes(1), ativo: true),
        new LimitadorDeConexoes());
    var t = isolado.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora).Token!;
    Exigir(!isolado.Desconectar(t, celular, servidor, agora.AddMinutes(1)));
    Exigir(!isolado.PodeControlar(t, celular, servidor, agora.AddMinutes(1)));
});

Testar("desligamento na bandeja revoga o token conectado", () =>
{
    var novo = autenticacao.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora.AddMinutes(1));
    Exigir(novo.Sucesso);
    autenticacao.DefinirAcessoDireto(false);
    Exigir(!autenticacao.PodeControlar(novo.Token, celular, servidor, agora.AddMinutes(1)));
    Exigir(autenticacao.Conectar(celular, servidor, null,
        "http", "192.168.1.3:49900", agora.AddMinutes(1)).CodigoHttp == 403);
});

Console.WriteLine($"VALIDAÇÃO COMPLETA: {executados} testes de segurança passaram.");
