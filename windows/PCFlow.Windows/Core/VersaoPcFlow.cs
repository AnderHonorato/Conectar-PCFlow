namespace PCFlow.Windows.Core;

/// <summary>
/// Versão do aplicativo e do protocolo.
///
/// O protocolo é comparado com o do app Android no handshake: quando não bate,
/// o usuário recebe uma instrução clara em vez de um "connection closed" seco.
/// Misturar APK de uma versão com EXE de outra foi uma das causas de falha.
/// </summary>
public static class VersaoPcFlow
{
    public const string App = "1.2.0";
    public const int Protocolo = 2;
}
