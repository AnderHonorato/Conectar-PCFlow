// O projeto usa WPF para a interface e WinForms apenas para o ícone da bandeja.
// Os dois trazem tipos de mesmo nome (Button, Color, TextBox…), então os apelidos
// abaixo fixam a versão WPF em todo o projeto e evitam ambiguidade de compilação.
global using Application = System.Windows.Application;
global using Brush = System.Windows.Media.Brush;
global using Brushes = System.Windows.Media.Brushes;
global using Button = System.Windows.Controls.Button;
global using CheckBox = System.Windows.Controls.CheckBox;
global using Clipboard = System.Windows.Clipboard;
global using Color = System.Windows.Media.Color;
global using ComboBox = System.Windows.Controls.ComboBox;
global using HorizontalAlignment = System.Windows.HorizontalAlignment;
global using Label = System.Windows.Controls.Label;
global using MessageBox = System.Windows.MessageBox;
global using Orientation = System.Windows.Controls.Orientation;
global using TextBox = System.Windows.Controls.TextBox;
