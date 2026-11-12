// Geometria do minimapa; nunca envia comandos ao Windows.
export function geometriaMiniatura(iw,ih,mw,mh){
 if(![iw,ih,mw,mh].every(n=>Number.isFinite(n)&&n>0))return null;
 const s=Math.min(mw/iw,mh/ih);
 return {x:(mw-iw*s)/2,y:(mh-ih*s)/2,largura:iw*s,altura:ih*s};
}
export function normalizarPonto(x,y,r){
 if(!r||![x,y,r.x,r.y,r.largura,r.altura].every(Number.isFinite)||r.largura<=0||r.altura<=0)return null;
 return {x:Math.max(0,Math.min(1,(x-r.x)/r.largura)),y:Math.max(0,Math.min(1,(y-r.y)/r.altura))};
}
// Impede faixas pretas e saltos ao deslocar imagens maiores que o visor.
export function limitarPan(panX,panY,larguraRenderizada,alturaRenderizada,larguraVisivel,alturaVisivel){
 if(![panX,panY,larguraRenderizada,alturaRenderizada,larguraVisivel,alturaVisivel].every(Number.isFinite))return null;
 const limiteX=Math.max(0,(larguraRenderizada-larguraVisivel)/2);
 const limiteY=Math.max(0,(alturaRenderizada-alturaVisivel)/2);
 return {x:Math.max(-limiteX,Math.min(limiteX,panX)),y:Math.max(-limiteY,Math.min(limiteY,panY))};
}
export function posicaoPan(nx,ny,iw,ih,vw,vh){
 if(![nx,ny,iw,ih,vw,vh].every(Number.isFinite)||iw<=0||ih<=0||vw<=0||vh<=0)return null;
 return limitarPan((.5-Math.max(0,Math.min(1,nx)))*iw,(.5-Math.max(0,Math.min(1,ny)))*ih,iw,ih,vw,vh);
}
