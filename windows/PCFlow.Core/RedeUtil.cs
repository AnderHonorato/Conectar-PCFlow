using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace PCFlow.Core;

public static class RedeUtil
{
    private static readonly object Trava = new();
    private static IReadOnlyList<IPAddress> _cache = [];
    private static DateTime _lidoEm = DateTime.MinValue;

    /// <summary>IPv4 da interface ativa mais provável para a LAN.</summary>
    public static string EnderecoLocal()
    {
        foreach (var ip in EnderecosLocais()) return ip.ToString();
        return "127.0.0.1";
    }

    /// <summary>
    /// Enumerar interfaces é caro e a interface consulta isso a cada segundo,
    /// então o resultado fica em cache por 5 s (troca de Wi‑Fi continua sendo detectada).
    /// </summary>
    public static IReadOnlyList<IPAddress> EnderecosLocais()
    {
        lock (Trava)
        {
            if (DateTime.UtcNow - _lidoEm < TimeSpan.FromSeconds(5)) return _cache;
            _cache = Consultar();
            _lidoEm = DateTime.UtcNow;
            return _cache;
        }
    }

    private static List<IPAddress> Consultar()
    {
        var lista = new List<IPAddress>();
        try
        {
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                foreach (var ua in ni.GetIPProperties().UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (IPAddress.IsLoopback(ua.Address)) continue;
                    if (ua.Address.ToString().StartsWith("169.254.", StringComparison.Ordinal)) continue;
                    lista.Add(ua.Address);
                }
            }
        }
        catch (NetworkInformationException) { }
        // Wi‑Fi/Ethernet privados primeiro; virtuais (Hyper-V, WSL, VPN) por último.
        return lista.OrderByDescending(EhFaixaDomestica).ToList();
    }

    private static bool EhFaixaDomestica(IPAddress ip)
    {
        var b = ip.GetAddressBytes();
        return b[0] == 192 && b[1] == 168;
    }

    /// <summary>
    /// Endereço da rede é privado (RFC1918 / link-local / loopback).
    /// Usado pelo modo "somente rede local".
    /// </summary>
    public static bool EhRedePrivada(IPAddress ip)
    {
        if (ip.AddressFamily == AddressFamily.InterNetworkV6)
        {
            if (IPAddress.IsLoopback(ip)) return true;
            if (ip.IsIPv4MappedToIPv6) return EhRedePrivada(ip.MapToIPv4());
            return ip.IsIPv6LinkLocal || ip.IsIPv6SiteLocal || (ip.GetAddressBytes()[0] & 0xFE) == 0xFC;
        }
        var b = ip.GetAddressBytes();
        return b[0] switch
        {
            10 => true,
            127 => true,
            169 when b[1] == 254 => true,
            172 when b[1] >= 16 && b[1] <= 31 => true,
            192 when b[1] == 168 => true,
            _ => false
        };
    }
}
