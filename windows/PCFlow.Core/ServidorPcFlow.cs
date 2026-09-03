using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;

namespace PCFlow.Core;

public sealed record EstadoServidor(bool Ativo, bool Pausado, int Conectados, string Mensagem);

/// <summary>
/// Servidor de controle do PCFlow.
///
/// Diferenças em relação à v1, que era a causa das falhas relatadas:
///  - Iniciar/Parar podem ser chamados várias vezes (o CancellationTokenSource é recriado);
///  - falha ao abrir a porta vira evento de erro em vez de exceção não tratada que fechava o app;
///  - acesso à lista de dispositivos é sincronizado (várias sessões gravavam ao mesmo tempo);
///  - heartbeat/timeout detectam queda de Wi‑Fi em segundos em vez de travar para sempre;
///  - PIN expira, é rotacionado e tem bloqueio por força bruta;
///  - linhas gigantes são recusadas antes de alocar memória.
/// </summary>
public sealed class ServidorPcFlow : IAsyncDisposable
{
    private readonly ArmazenamentoConfiguracao _armazenamento;
    private readonly ServicosPlataforma _plataforma;
    private readonly ServicoArquivos _arquivos;
    private readonly object _trava = new();
    private readonly List<SessaoCliente> _sessoes = [];

    private ConfiguracaoPcFlow _configuracao;
    private TcpListener? _tcp;
    private CancellationTokenSource? _cts;
    private Task? _aceitador;
    private ServicoDescoberta? _descoberta;

    public GerenciadorPin Pin { get; } = new();
    public Registro Log { get; } = new();
    public bool Ativo { get; private set; }
    public bool Pausado { get; private set; }
    public string? UltimoErro { get; private set; }
    public ConfiguracaoPcFlow Configuracao => _configuracao;
    public int Conectados { get { lock (_trava) return _sessoes.Count(s => s.Autenticada); } }
    public bool DescobertaAtiva => _descoberta?.Ativo ?? false;
    public string NomeMaquina => _plataforma.NomeMaquina;

    public event Action<EstadoServidor>? EstadoAlterado;
    public event Action? DispositivosAlterados;
    /// <summary>Disparado quando um dispositivo novo pede autorização. Retorne true para permitir.</summary>
    public Func<string, string, bool>? AutorizarNovoDispositivo;

    public ServidorPcFlow(ServicosPlataforma plataforma, ArmazenamentoConfiguracao? armazenamento = null)
    {
        _plataforma = plataforma;
        _armazenamento = armazenamento ?? new ArmazenamentoConfiguracao();
        _configuracao = _armazenamento.Carregar();
        _arquivos = new ServicoArquivos(() => _configuracao);
    }

    public IReadOnlyList<DispositivoAutorizado> Dispositivos
    {
        get
        {
            lock (_trava)
            {
                var conectados = _sessoes.Where(s => s.Autenticada).Select(s => s.DispositivoId).ToHashSet();
                foreach (var d in _configuracao.Dispositivos) d.Conectado = conectados.Contains(d.Id);
                return _configuracao.Dispositivos.OrderByDescending(d => d.Conectado)
                                                 .ThenByDescending(d => d.UltimaConexao).ToList();
            }
        }
    }

    public void SalvarConfiguracao()
    {
        lock (_trava) _armazenamento.Salvar(_configuracao);
    }

    public bool Iniciar()
    {
        if (Ativo) return true;
        var porta = _configuracao.PortaControle > 0 ? _configuracao.PortaControle : Protocolo.PortaControle;
        try
        {
            _tcp = new TcpListener(IPAddress.Any, porta);
            _tcp.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _tcp.Start();
        }
        catch (SocketException ex)
        {
            UltimoErro = ex.SocketErrorCode == SocketError.AddressAlreadyInUse
                ? $"A porta {porta} já está em uso. Outra cópia do PCFlow pode estar aberta."
                : $"Não foi possível abrir a porta {porta}: {ex.SocketErrorCode}.";
            Log.Escrever(Categoria.Erro, UltimoErro);
            _tcp = null;
            Notificar();
            return false;
        }

        _cts = new CancellationTokenSource();
        _aceitador = AceitarAsync(_cts.Token);
        _descoberta = new ServicoDescoberta(() => _plataforma.NomeMaquina, () => porta,
            m => Log.Escrever(Categoria.Descoberta, m));
        if (!_descoberta.Iniciar())
            Log.Escrever(Categoria.Descoberta, _descoberta.UltimoErro ?? "Descoberta indisponível.");

        Ativo = true;
        Pausado = false;
        UltimoErro = null;
        Log.Escrever(Categoria.Conexao, $"Servidor ativo em {RedeUtil.EnderecoLocal()}:{porta}");
        Notificar();
        return true;
    }

    public async Task PararAsync()
    {
        if (!Ativo) return;
        Ativo = false;
        if (_cts is not null) await _cts.CancelAsync().ConfigureAwait(false);
        try { _tcp?.Stop(); } catch (SocketException) { }
        _tcp = null;

        SessaoCliente[] copia;
        lock (_trava) { copia = _sessoes.ToArray(); _sessoes.Clear(); }
        foreach (var s in copia) s.Encerrar();

        if (_descoberta is not null) { await _descoberta.DisposeAsync().ConfigureAwait(false); _descoberta = null; }
        if (_aceitador is not null) { try { await _aceitador.ConfigureAwait(false); } catch { } _aceitador = null; }
        _cts?.Dispose();
        _cts = null;

        Log.Escrever(Categoria.Conexao, "Servidor parado");
        Notificar();
    }

    public async Task<bool> ReiniciarAsync()
    {
        await PararAsync().ConfigureAwait(false);
        await Task.Delay(250).ConfigureAwait(false);
        return Iniciar();
    }

    public void AlternarPausa()
    {
        Pausado = !Pausado;
        Log.Escrever(Categoria.Conexao, Pausado ? "Servidor pausado" : "Servidor retomado");
        Notificar();
    }

    /// <summary>Porta TCP realmente em uso (útil quando a configuração muda).</summary>
    public int PortaEmUso => (_tcp?.LocalEndpoint as IPEndPoint)?.Port
                             ?? (_configuracao.PortaControle > 0 ? _configuracao.PortaControle : Protocolo.PortaControle);

    private void Notificar()
        => EstadoAlterado?.Invoke(new EstadoServidor(Ativo, Pausado, Conectados,
            UltimoErro ?? (Ativo ? (Pausado ? "Servidor pausado" : "Servidor ativo") : "Servidor parado")));

    private async Task AceitarAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcp is not null)
        {
            TcpClient cliente;
            try
            {
                cliente = await _tcp.AcceptTcpClientAsync(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (SocketException ex)
            {
                Log.Escrever(Categoria.Erro, $"Falha ao aceitar conexão: {ex.SocketErrorCode}");
                continue;
            }

            var sessao = new SessaoCliente(cliente, this, _plataforma, _arquivos);
            lock (_trava) _sessoes.Add(sessao);
            _ = Task.Run(async () =>
            {
                try { await sessao.ExecutarAsync(ct).ConfigureAwait(false); }
                catch (Exception ex) { Log.Escrever(Categoria.Erro, $"Sessão: {ex.GetType().Name}"); }
                finally
                {
                    lock (_trava) _sessoes.Remove(sessao);
                    DispositivosAlterados?.Invoke();
                    Notificar();
                }
            }, ct);
        }
    }

    // ---- pareamento e autorização (usado pela sessão) ----

    internal bool PermiteOrigem(IPAddress ip)
        => !_configuracao.SomenteRedeLocal || RedeUtil.EhRedePrivada(ip);

    internal DispositivoAutorizado? BuscarDispositivo(string id)
    {
        lock (_trava) return _configuracao.Dispositivos.FirstOrDefault(d => d.Id == id);
    }

    internal enum ResultadoHandshake { Pareado, Reconectado, PinInvalido, Bloqueado, Recusado, NaoAutorizado }

    internal ResultadoHandshake Autenticar(Mensagem ola, IPAddress origem,
        out DispositivoAutorizado? dispositivo, out string? detalhe)
    {
        detalhe = null;
        dispositivo = null;
        var id = ola.DispositivoId;
        if (string.IsNullOrWhiteSpace(id)) { detalhe = "Identificação ausente."; return ResultadoHandshake.Recusado; }

        var existente = BuscarDispositivo(id);
        if (existente is not null && !string.IsNullOrEmpty(ola.Token))
        {
            if (existente.Bloqueado) { detalhe = "Dispositivo bloqueado no PC."; return ResultadoHandshake.NaoAutorizado; }
            var esperado = Encoding.UTF8.GetBytes(existente.Token);
            var recebido = Encoding.UTF8.GetBytes(ola.Token!);
            if (esperado.Length != recebido.Length || !CryptographicOperations.FixedTimeEquals(esperado, recebido))
            {
                detalhe = "Credencial inválida. Refaça o pareamento com o PIN.";
                Log.Escrever(Categoria.Autenticacao, $"Token inválido de {origem}");
                return ResultadoHandshake.NaoAutorizado;
            }
            lock (_trava)
            {
                existente.UltimaConexao = DateTime.Now;
                existente.UltimoIp = origem.ToString();
                if (!string.IsNullOrWhiteSpace(ola.Nome)) existente.Nome = ola.Nome!;
                _armazenamento.Salvar(_configuracao);
            }
            dispositivo = existente;
            Log.Escrever(Categoria.Autenticacao, $"{existente.Nome} reconectou de {origem}");
            return ResultadoHandshake.Reconectado;
        }

        // Dispositivo novo (ou token perdido): exige PIN.
        if (Pin.EstaBloqueado(origem, out var restante))
        {
            detalhe = $"Muitas tentativas. Aguarde {Math.Ceiling(restante.TotalSeconds)}s.";
            Log.Escrever(Categoria.Autenticacao, $"Bloqueio por força bruta para {origem}");
            return ResultadoHandshake.Bloqueado;
        }
        if (!Pin.Validar(ola.Pin, origem))
        {
            detalhe = "PIN incorreto ou expirado. Veja o código atual no PC.";
            Log.Escrever(Categoria.Autenticacao, $"PIN incorreto vindo de {origem}");
            return ResultadoHandshake.PinInvalido;
        }

        var nome = string.IsNullOrWhiteSpace(ola.Nome) ? "Celular" : ola.Nome!;
        if (_configuracao.PerguntarAntesDeNovoDispositivo && AutorizarNovoDispositivo is not null)
        {
            if (!AutorizarNovoDispositivo(nome, origem.ToString()))
            {
                detalhe = "Conexão recusada no PC.";
                Log.Escrever(Categoria.Autenticacao, $"Usuário recusou {nome} ({origem})");
                return ResultadoHandshake.Recusado;
            }
        }

        var novo = existente ?? new DispositivoAutorizado { Id = id! };
        novo.Nome = nome;
        novo.Modelo = ola.Modelo ?? "";
        novo.Token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
        novo.UltimoIp = origem.ToString();
        novo.UltimaConexao = DateTime.Now;
        novo.Bloqueado = false;
        lock (_trava)
        {
            if (existente is null) _configuracao.Dispositivos.Add(novo);
            _armazenamento.Salvar(_configuracao);
        }
        Pin.Renovar();
        dispositivo = novo;
        Log.Escrever(Categoria.Autenticacao, $"{nome} pareado a partir de {origem}");
        DispositivosAlterados?.Invoke();
        return ResultadoHandshake.Pareado;
    }

    internal void RegistrarConectado(DispositivoAutorizado d)
    {
        DispositivosAlterados?.Invoke();
        Notificar();
    }

    public void RemoverDispositivo(string id)
    {
        lock (_trava)
        {
            _configuracao.Dispositivos.RemoveAll(d => d.Id == id);
            _armazenamento.Salvar(_configuracao);
            foreach (var s in _sessoes.Where(s => s.DispositivoId == id)) s.Encerrar();
        }
        DispositivosAlterados?.Invoke();
    }

    public void DefinirBloqueio(string id, bool bloqueado)
    {
        lock (_trava)
        {
            var d = _configuracao.Dispositivos.FirstOrDefault(x => x.Id == id);
            if (d is null) return;
            d.Bloqueado = bloqueado;
            _armazenamento.Salvar(_configuracao);
            if (bloqueado) foreach (var s in _sessoes.Where(s => s.DispositivoId == id)) s.Encerrar();
        }
        DispositivosAlterados?.Invoke();
    }

    public void RenomearDispositivo(string id, string nome)
    {
        lock (_trava)
        {
            var d = _configuracao.Dispositivos.FirstOrDefault(x => x.Id == id);
            if (d is null || string.IsNullOrWhiteSpace(nome)) return;
            d.Nome = nome.Trim();
            _armazenamento.Salvar(_configuracao);
        }
        DispositivosAlterados?.Invoke();
    }

    public async ValueTask DisposeAsync() => await PararAsync().ConfigureAwait(false);
}
