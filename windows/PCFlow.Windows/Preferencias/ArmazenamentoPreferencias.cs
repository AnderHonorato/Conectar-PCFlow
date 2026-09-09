using System.IO;

namespace PCFlow.Windows.Preferencias;

/// <summary>
/// Guarda as preferências de interface em Documentos\PCFlow\interface.json.
/// Fica em Documentos e não em AppData de propósito: o usuário consegue abrir,
/// ler e apagar o arquivo sem procurar pasta escondida — e é lá que o PCFlow já
/// escreve diagnóstico e relatório de erro.
/// </summary>
public sealed class ArmazenamentoPreferencias
{
    private readonly string _arquivo;
    private readonly object _trava = new();

    public ArmazenamentoPreferencias()
    {
        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PCFlow");
        Directory.CreateDirectory(pasta);
        _arquivo = Path.Combine(pasta, "interface.json");
    }

    public PreferenciasInterface Carregar()
    {
        lock (_trava)
        {
            try
            {
                return File.Exists(_arquivo)
                    ? PreferenciasInterface.DeJson(File.ReadAllText(_arquivo))
                    : new PreferenciasInterface();
            }
            catch (IOException) { return new PreferenciasInterface(); }
            catch (UnauthorizedAccessException) { return new PreferenciasInterface(); }
        }
    }

    public void Salvar(PreferenciasInterface preferencias)
    {
        lock (_trava)
        {
            // Preferência de aparência não vale derrubar o aplicativo: se o disco
            // recusar a escrita, a tela segue funcionando com o que está na memória.
            try { File.WriteAllText(_arquivo, preferencias.ParaJson()); }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }
    }
}
