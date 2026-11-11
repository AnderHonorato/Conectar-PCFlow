namespace FlowTutum.Next;

public readonly record struct MonitorGeometry(int Left, int Top, int Width, int Height) {
    public bool IsValid => Width > 0 && Height > 0;
}
public readonly record struct ScreenPoint(int X, int Y);
public static class Coordinates {
    public static ScreenPoint FromNormalized(double nx,double ny,MonitorGeometry m) {
        if (!m.IsValid || !double.IsFinite(nx) || !double.IsFinite(ny) || nx < 0 || nx > 1 || ny < 0 || ny > 1)
            throw new ArgumentOutOfRangeException(nameof(nx),"Coordenadas normalizadas invalidas.");
        int x = m.Left + (int)Math.Round(nx * (m.Width-1),MidpointRounding.AwayFromZero);
        int y = m.Top + (int)Math.Round(ny * (m.Height-1),MidpointRounding.AwayFromZero);
        return new ScreenPoint(x,y);
    }
    public static (double X,double Y) FromClient(double clientX,double clientY,double boxLeft,double boxTop,double boxWidth,double boxHeight) {
        if (!double.IsFinite(clientX) || !double.IsFinite(clientY) || !double.IsFinite(boxLeft) || !double.IsFinite(boxTop) ||
            !double.IsFinite(boxWidth) || !double.IsFinite(boxHeight) ||
            boxWidth <= 0 || boxHeight <= 0) throw new ArgumentException("Retangulo invalido.");
        return (Math.Clamp((clientX-boxLeft)/boxWidth,0,1),Math.Clamp((clientY-boxTop)/boxHeight,0,1));
    }
}
