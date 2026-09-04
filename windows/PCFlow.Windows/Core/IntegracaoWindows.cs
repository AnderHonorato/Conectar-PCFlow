using System.Diagnostics;
using System.Runtime.Versioning;
using Microsoft.Win32;

namespace PCFlow.Windows.Core;

/// <summary>Regras de firewall e inicialização automática.</summary>
[SupportedOSPlatform("windows")]
public static class IntegracaoWindows
{
    private const string NomeRegraTcp = "PCFlow (portas TCP)";
    private const string NomeRegraUdp = "PCFlow (descoberta UDP)";
    private const string ChaveRun = @"Software\Microsoft\Windows\CurrentVersion\Run";

    /// <summary>
    /// Cria as regras de entrada apenas para o perfil privado.
    /// Sem isso, o Windows bloqueia silenciosamente a porta e o celular
    /// "acha o PC mas não conecta" — a causa nº 1 de falha de conexão.
    /// </summary>
    public static bool GarantirRegrasFirewall(out string detalhe)
    {
        try
        {
            var exe = Environment.ProcessPath ?? "";
            RemoverRegra(NomeRegraTcp);
            RemoverRegra(NomeRegraUdp);

            // As quatro portas do PCFlow: controle, tela, arquivos e descoberta.
            var portasTcp = $"{ServidorPcFlow.PortaControle},{ServidorPcFlow.PortaTela},{ServidorArquivosPcFlow.Porta}";
            var okTcp = Netsh(
                $"advfirewall firewall add rule name=\"{NomeRegraTcp}\" dir=in action=allow " +
                $"protocol=TCP localport={portasTcp} profile=private,domain " +
                (exe.Length > 0 ? $"program=\"{exe}\"" : ""));
            var okUdp = Netsh(
                $"advfirewall firewall add rule name=\"{NomeRegraUdp}\" dir=in action=allow " +
                $"protocol=UDP localport={ServidorPcFlow.PortaDescoberta} profile=private,domain " +
                (exe.Length > 0 ? $"program=\"{exe}\"" : ""));

            if (okTcp && okUdp)
            {
                detalhe = $"Regras criadas para TCP {portasTcp} e UDP {ServidorPcFlow.PortaDescoberta}, apenas na rede privada.";
                return true;
            }
            detalhe = "O Windows recusou a criação das regras. Abra o PCFlow como administrador uma vez para concluir.";
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
