using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using Microsoft.Win32;
using PCFlow.Core;

namespace PCFlow.Windows.Plataforma;

[SupportedOSPlatform("windows")]
public sealed class EnergiaWindows : IControleEnergia
{
    [DllImport("user32.dll")] private static extern bool LockWorkStation();
    [DllImport("user32.dll")]
    private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    private static readonly IntPtr HWND_BROADCAST = new(0xffff);
    private const uint WM_SYSCOMMAND = 0x0112;
    private static readonly IntPtr SC_MONITORPOWER = new(0xF170);

    public bool Executar(string acao)
    {
        try
        {
            switch (acao.ToLowerInvariant())
            {
                case "lock" or "bloquear":
                    return LockWorkStation();
                case "monitoroff" or "monitor":
                    SendMessage(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, new IntPtr(2));
                    return true;
                case "sleep" or "suspender":
                    return Rodar("rundll32.exe", "powrprof.dll,SetSuspendState 0,1,0");
                case "hibernate" or "hibernar":
                    return Rodar("shutdown", "/h");
                case "shutdown" or "desligar":
                    return Rodar("shutdown", "/s /t 0");
                case "restart" or "reiniciar":
                    return Rodar("shutdown", "/r /t 0");
                case "signout" or "sair":
                    return Rodar("shutdown", "/l");
                default:
                    return false;
            }
        }
        catch (Exception) { return false; }
    }

    private static bool Rodar(string arquivo, string argumentos)
    {
        try
        {
            Process.Start(new ProcessStartInfo(arquivo, argumentos)
            {
                CreateNoWindow = true,
                UseShellExecute = false
            });
            return true;
        }
        catch (Exception) { return false; }
    }
}

/// <summary>
/// Área de transferência do Windows. Exige thread STA, por isso cada operação
/// roda numa thread dedicada — chamar direto da thread do socket lançava exceção.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class AreaTransferenciaWindows : IAreaTransferencia
{
    public string? Ler() => EmSta(() =>
        System.Windows.Clipboard.ContainsText() ? System.Windows.Clipboard.GetText() : null);

    public void Escrever(string texto) => EmSta<object?>(() =>
    {
        if (!string.IsNullOrEmpty(texto)) System.Windows.Clipboard.SetText(texto);
        return null;
    });

    private static T? EmSta<T>(Func<T?> acao)
    {
        T? resultado = default;
        var t = new Thread(() =>
        {
            try { resultado = acao(); }
            catch (Exception) { resultado = default; }
        });
        t.SetApartmentState(ApartmentState.STA);
        t.IsBackground = true;
        t.Start();
        t.Join(2000);
        return resultado;
    }
}

/// <summary>Captura o monitor principal, redimensiona e comprime em JPEG.</summary>
[SupportedOSPlatform("windows")]
public sealed class CapturaTelaWindows : ICapturaTela
{
    private static readonly ImageCodecInfo? CodecJpeg =
        ImageCodecInfo.GetImageEncoders().FirstOrDefault(c => c.FormatID == ImageFormat.Jpeg.Guid);

    private readonly object _trava = new();

    public QuadroTela? Capturar(int larguraMaxima, int qualidade)
    {
        lock (_trava)
        {
            try
            {
                var (x, y, largura, altura) = EntradaWindows.MesaVirtual();
                if (largura <= 0 || altura <= 0) return null;

                using var completo = new Bitmap(largura, altura, PixelFormat.Format32bppArgb);
                using (var g = Graphics.FromImage(completo))
                    g.CopyFromScreen(x, y, 0, 0, new Size(largura, altura), CopyPixelOperation.SourceCopy);

                var escala = Math.Min(1.0, larguraMaxima / (double)largura);
                var novaLargura = Math.Max(1, (int)(largura * escala));
                var novaAltura = Math.Max(1, (int)(altura * escala));

                using var reduzido = new Bitmap(novaLargura, novaAltura, PixelFormat.Format24bppRgb);
                using (var g = Graphics.FromImage(reduzido))
                {
                    g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBilinear;
                    g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighSpeed;
                    g.DrawImage(completo, 0, 0, novaLargura, novaAltura);
                }

                using var memoria = new MemoryStream();
                if (CodecJpeg is not null)
                {
                    using var parametros = new EncoderParameters(1);
                    parametros.Param[0] = new EncoderParameter(Encoder.Quality, (long)qualidade);
                    reduzido.Save(memoria, CodecJpeg, parametros);
                }
                else
                {
                    reduzido.Save(memoria, ImageFormat.Jpeg);
                }
                return new QuadroTela(memoria.ToArray(), novaLargura, novaAltura);
            }
            catch (Exception) { return null; }
        }
    }
}

/// <summary>
/// Lista atalhos do Menu Iniciar do usuário e do sistema, mais alguns comandos fixos.
/// Só executa itens que estavam nessa lista — nunca um caminho arbitrário vindo da rede.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class LancadorWindows : ILancadorAplicativos
{
    private readonly object _trava = new();
    private List<AtalhoPc> _cache = [];
    private DateTime _atualizadoEm = DateTime.MinValue;

    private static readonly AtalhoPc[] Fixos =
    [
        new("sys:explorer", "Explorador de Arquivos", "explorer.exe"),
        new("sys:desktop", "Mostrar a área de trabalho", "@combo", "win+d"),
        new("sys:alttab", "Alternar janelas", "@combo", "alt+tab"),
        new("sys:lock", "Bloquear (Win+L)", "@combo", "win+l"),
        new("sys:settings", "Configurações do Windows", "ms-settings:"),
        new("sys:taskmgr", "Gerenciador de Tarefas", "@combo", "ctrl+shift+esc"),
    ];

    public IReadOnlyList<AtalhoPc> Listar()
    {
        lock (_trava)
        {
            if (_cache.Count > 0 && DateTime.Now - _atualizadoEm < TimeSpan.FromMinutes(5)) return _cache;

            var itens = new List<AtalhoPc>(Fixos);
            foreach (var pasta in new[]
            {
                Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
                Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu)
            })
            {
                if (string.IsNullOrEmpty(pasta) || !Directory.Exists(pasta)) continue;
                try
                {
                    foreach (var lnk in Directory.EnumerateFiles(pasta, "*.lnk", SearchOption.AllDirectories))
                    {
                        var nome = Path.GetFileNameWithoutExtension(lnk);
                        if (nome.Contains("uninstall", StringComparison.OrdinalIgnoreCase)) continue;
                        if (nome.Contains("desinstal", StringComparison.OrdinalIgnoreCase)) continue;
                        var id = "lnk:" + Convert.ToHexString(
                            System.Security.Cryptography.MD5.HashData(
                                System.Text.Encoding.UTF8.GetBytes(lnk.ToLowerInvariant())))[..12];
                        itens.Add(new AtalhoPc(id, nome, lnk));
                    }
                }
                catch (Exception) { /* pasta protegida: segue com o que já leu */ }
            }

            _cache = itens
                .GroupBy(i => i.Nome, StringComparer.OrdinalIgnoreCase).Select(g => g.First())
                .OrderBy(i => i.Nome, StringComparer.OrdinalIgnoreCase)
                .Take(300).ToList();
            _atualizadoEm = DateTime.Now;
            return _cache;
        }
    }

    public bool Executar(string id)
    {
        var alvo = Listar().FirstOrDefault(a => a.Id == id);
        if (alvo is null) return false;
        try
        {
            if (alvo.Alvo == "@combo")
            {
                var partes = (alvo.Argumentos ?? "").Split('+',
                    StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
                if (partes.Length == 0) return false;
                new EntradaWindows().PressionarTecla(partes[^1], partes[..^1]);
                return true;
            }
            Process.Start(new ProcessStartInfo(alvo.Alvo) { UseShellExecute = true });
            return true;
        }
        catch (Exception) { return false; }
    }
}

/// <summary>Regras de firewall e inicialização automática.</summary>
[SupportedOSPlatform("windows")]
public static class IntegracaoWindows
{
    private const string NomeRegraTcp = "PCFlow (controle TCP)";
    private const string NomeRegraUdp = "PCFlow (descoberta UDP)";
    private const string ChaveRun = @"Software\Microsoft\Windows\CurrentVersion\Run";

    /// <summary>
    /// Cria as regras de entrada apenas para o perfil privado.
    /// Sem isso, o Windows bloqueia silenciosamente a porta e o celular
    /// "acha o PC mas não conecta" — a causa nº 1 de falha de conexão.
    /// </summary>
    public static bool GarantirRegrasFirewall(int portaControle, out string detalhe)
    {
        try
        {
            var exe = Environment.ProcessPath ?? "";
            RemoverRegra(NomeRegraTcp);
            RemoverRegra(NomeRegraUdp);
            var okTcp = Netsh(
                $"advfirewall firewall add rule name=\"{NomeRegraTcp}\" dir=in action=allow " +
                $"protocol=TCP localport={portaControle} profile=private,domain " +
                (exe.Length > 0 ? $"program=\"{exe}\"" : ""));
            var okUdp = Netsh(
                $"advfirewall firewall add rule name=\"{NomeRegraUdp}\" dir=in action=allow " +
                $"protocol=UDP localport={Protocolo.PortaDescoberta} profile=private,domain " +
                (exe.Length > 0 ? $"program=\"{exe}\"" : ""));

            if (okTcp && okUdp)
            {
                detalhe = $"Regras criadas para TCP {portaControle} e UDP {Protocolo.PortaDescoberta} (rede privada).";
                return true;
            }
            detalhe = "O Windows recusou a criação das regras. Execute o PCFlow como administrador uma vez.";
            return false;
        }
        catch (Exception ex)
        {
            detalhe = $"Não foi possível configurar o firewall: {ex.Message}";
            return false;
        }
    }

    public static bool RegrasExistem()
    {
        try
        {
            var p = Process.Start(new ProcessStartInfo("netsh",
                $"advfirewall firewall show rule name=\"{NomeRegraTcp}\"")
            {
                CreateNoWindow = true,
                UseShellExecute = false,
                RedirectStandardOutput = true
            });
            if (p is null) return false;
            p.StandardOutput.ReadToEnd();
            p.WaitForExit(4000);
            return p.ExitCode == 0;
        }
        catch (Exception) { return false; }
    }

    private static void RemoverRegra(string nome)
        => Netsh($"advfirewall firewall delete rule name=\"{nome}\"");

    private static bool Netsh(string argumentos)
    {
        try
        {
            var p = Process.Start(new ProcessStartInfo("netsh", argumentos)
            {
                CreateNoWindow = true,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            });
            if (p is null) return false;
            p.StandardOutput.ReadToEnd();
            p.StandardError.ReadToEnd();
            p.WaitForExit(8000);
            return p.ExitCode == 0;
        }
        catch (Exception) { return false; }
    }

    public static bool IniciaComWindows()
    {
        try
        {
            using var chave = Registry.CurrentUser.OpenSubKey(ChaveRun, false);
            return chave?.GetValue("PCFlow") is not null;
        }
        catch (Exception) { return false; }
    }

    public static void DefinirIniciaComWindows(bool ativar)
    {
        try
        {
            using var chave = Registry.CurrentUser.OpenSubKey(ChaveRun, true);
            if (chave is null) return;
            if (ativar)
            {
                var exe = Environment.ProcessPath;
                if (!string.IsNullOrEmpty(exe)) chave.SetValue("PCFlow", $"\"{exe}\" --minimizado");
            }
            else chave.DeleteValue("PCFlow", false);
        }
        catch (Exception) { }
    }

    public static bool EhAdministrador()
    {
        try
        {
            using var identidade = System.Security.Principal.WindowsIdentity.GetCurrent();
            return new System.Security.Principal.WindowsPrincipal(identidade)
                .IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
        }
        catch (Exception) { return false; }
    }

    /// <summary>Reabre o PCFlow com elevação para gravar as regras de firewall.</summary>
    public static bool ReabrirComoAdministrador(string argumentos)
    {
        try
        {
            var exe = Environment.ProcessPath;
            if (string.IsNullOrEmpty(exe)) return false;
            Process.Start(new ProcessStartInfo(exe, argumentos) { UseShellExecute = true, Verb = "runas" });
            return true;
        }
        catch (Exception) { return false; }
    }
}
