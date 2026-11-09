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

Console.WriteLine($"VALIDAÇÃO COMPLETA: {executados} testes de segurança passaram.");
