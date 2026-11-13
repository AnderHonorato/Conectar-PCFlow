using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

namespace FlowTutum.Next;

/// <summary>Tray por usuario, em thread STA, para pareamento e controle local.</summary>
public static class TrayHost {
    public static string PauseFile => Path.Combine(Environment.GetFolderPath(
        Environment.SpecialFolder.ApplicationData),"FlowTutumNext","pause.flag");

    public static void Start(string pin,int port,Action stopServer,Action? revokeLocal=null) {
        var thread=new Thread(()=>{
            using var context=new ApplicationContext();
            using var menu=new ContextMenuStrip();
            using var icon=new NotifyIcon {
                Icon=SystemIcons.Application,
                Text="Flow Tutum Next - controle remoto",
                Visible=true,
                ContextMenuStrip=menu
            };
            var showPin=new ToolStripMenuItem("Mostrar codigo de pareamento");
            showPin.Click+=(_,_)=>MessageBox.Show(
                "Codigo temporario: "+pin+"\n\nUse somente nos seus dispositivos autorizados.",
                "Flow Tutum Next - Pareamento",MessageBoxButtons.OK,MessageBoxIcon.Information);
            var browser=new ToolStripMenuItem("Abrir controle local");
            browser.Click+=(_,_)=>{
                try{Process.Start(new ProcessStartInfo("http://127.0.0.1:"+port+"/"){UseShellExecute=true});}
                catch(Exception ex){MessageBox.Show(ex.Message,"Flow Tutum Next");}
            };
            var status=new ToolStripMenuItem("Status do desktop");
            status.Click+=(_,_)=>{
                var s=SessionStatus.Read();
                MessageBox.Show(s.Message+"\n\nSessao: "+s.SessionId,
                    "Flow Tutum Next",MessageBoxButtons.OK,
                    s.Available?MessageBoxIcon.Information:MessageBoxIcon.Warning);
            };
            var wifiToggle=new ToolStripMenuItem();
            void UpdateWifiText(){
                wifiToggle.Text=LocalAccessSettings.Enabled?
                    "Wi-Fi direto ATIVADO — desativar":"Wi-Fi direto DESATIVADO — ativar";
                wifiToggle.Enabled=LocalAccessSettings.Available;
                wifiToggle.Checked=LocalAccessSettings.Enabled;
            }
            UpdateWifiText();
            menu.Opening+=(_,_)=>UpdateWifiText();
            wifiToggle.Click+=(_,_)=>{
                bool newState=!LocalAccessSettings.Enabled;
                if(!newState){
                    var answer=MessageBox.Show(
                        "Desativar agora o controle automatico pelo Wi-Fi? "+
                        "Os celulares locais conectados serao bloqueados imediatamente.",
                        "Flow Tutum Next - Wi-Fi",MessageBoxButtons.YesNo,
                        MessageBoxIcon.Question,MessageBoxDefaultButton.Button2);
                    if(answer!=DialogResult.Yes)return;
                }
                try {
                    LocalAccessSettings.SetEnabled(newState);
                    if(!newState)revokeLocal?.Invoke();
                    UpdateWifiText();
                    icon.ShowBalloonTip(2500,"Flow Tutum Next",
                        newState?"Wi-Fi direto ativado.":"Wi-Fi direto desativado.",
                        ToolTipIcon.Info);
                } catch(Exception ex) {
                    MessageBox.Show(ex.Message,"Flow Tutum Next",MessageBoxButtons.OK,MessageBoxIcon.Error);
                }
            };
            var pause=new ToolStripMenuItem("Desligar Flow Tutum (nao reiniciar)");
            pause.Click+=(_,_)=>{
                var answer=MessageBox.Show(
                    "Desligar agora o Flow Tutum Next? A inicializacao automatica ficara bloqueada ate voce abrir o aplicativo novamente.",
                    "Flow Tutum Next",MessageBoxButtons.YesNo,MessageBoxIcon.Question,
                    MessageBoxDefaultButton.Button2);
                if(answer!=DialogResult.Yes)return;
                Directory.CreateDirectory(Path.GetDirectoryName(PauseFile)!);
                File.WriteAllText(PauseFile,DateTime.UtcNow.ToString("O"));
                icon.Visible=false;
                stopServer();
                context.ExitThread();
            };
            menu.Items.AddRange(new ToolStripItem[]{browser,wifiToggle,status,showPin,new ToolStripSeparator(),pause});
            icon.DoubleClick+=(_,_)=>showPin.PerformClick();
            Application.Run(context);
            icon.Visible=false;
        }) {IsBackground=true,Name="FlowTutum-Tray"};
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
    }
}
