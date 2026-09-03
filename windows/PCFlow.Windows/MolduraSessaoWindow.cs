using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace PCFlow.Windows;

public sealed class MolduraSessaoWindow : Window
{
    public MolduraSessaoWindow()
    {
        WindowStyle = WindowStyle.None;
        AllowsTransparency = true;
        Background = Brushes.Transparent;
        Topmost = true;
        ShowInTaskbar = false;
        ResizeMode = ResizeMode.NoResize;
        IsHitTestVisible = false;
        Left = SystemParameters.VirtualScreenLeft;
        Top = SystemParameters.VirtualScreenTop;
        Width = SystemParameters.VirtualScreenWidth;
        Height = SystemParameters.VirtualScreenHeight;
        Content = new Border
        {
            BorderBrush = new SolidColorBrush(Color.FromRgb(242, 170, 46)),
            BorderThickness = new Thickness(4),
            Background = Brushes.Transparent
        };
    }
}
