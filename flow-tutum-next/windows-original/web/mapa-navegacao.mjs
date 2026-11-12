// Geometria pura do minimapa: nenhuma interação envia comandos ao Windows.
export function geometriaMiniatura(larguraImagem,alturaImagem,larguraMapa,alturaMapa){
 if(![larguraImagem,alturaImagem,larguraMapa,alturaMapa].every(n=>Number.isFinite(n)&&n>0))return null;
 const escala=Math.min(larguraMapa/larguraImagem,alturaMapa/alturaImagem);
 return {x:(larguraMapa-larguraImagem*escala)/2,y:(alturaMapa-alturaImagem*escala)/2,largura:larguraImagem*escala,altura:alturaImagem*escala};
}
export function posicaoPan(normalX,normalY,larguraImagemRenderizada,alturaImagemRenderizada,larguraVisivel,alturaVisivel){
 if(![normalX,normalY,larguraImagemRenderizada,alturaImagemRenderizada,larguraVisivel,alturaVisivel].every(Number.isFinite))return null;
 const x=Math.max(0,Math.min(1,normalX)),y=Math.max(0,Math.min(1,normalY));
 return {x:(0.5-x)*larguraImagemRenderizada,y:(0.5-y)*alturaImagemRenderizada};
}
export function normalizarPonto(x,y,retangulo){
 if(!retangulo||retangulo.largura<=0||retangulo.altura<=0)return null;
 return {x:Math.max(0,Math.min(1,(x-retangulo.x)/retangulo.largura)),y:Math.max(0,Math.min(1,(y-retangulo.y)/retangulo.altura))};
}
