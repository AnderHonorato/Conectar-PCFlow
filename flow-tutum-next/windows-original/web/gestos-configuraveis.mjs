// Funções puras para regular a resposta do touchpad sem alterar o protocolo.
export const LIMITES = Object.freeze({ponteiro:[0.5,3],rolagem:[0.5,3]});
export function limitar(valor,min,max){return Math.max(min,Math.min(max,valor));}
export function normalizarPreferencias(entrada={}){
 const numero=(v,padrao)=>v===null||v===undefined||v===''?padrao:Number(v);
 const ponteiro=numero(entrada.ponteiro,1.5),rolagem=numero(entrada.rolagem,1);
 return {ponteiro:Number.isFinite(ponteiro)?limitar(ponteiro,...LIMITES.ponteiro):1.5,
  rolagem:Number.isFinite(rolagem)?limitar(rolagem,...LIMITES.rolagem):1};
}
export function deslocarCursor(cursor,dx,dy,largura,altura,sensibilidade){
 if(![dx,dy,largura,altura,sensibilidade,cursor.x,cursor.y].every(Number.isFinite))return cursor;
 return {x:limitar(cursor.x+dx/Math.max(1,largura)*sensibilidade,0,1),
 y:limitar(cursor.y+dy/Math.max(1,altura)*sensibilidade,0,1)};
}
export function calcularRolagem(delta,fator=1){
 if(!Number.isFinite(delta)||!Number.isFinite(fator))return 0;
 return limitar(Math.round(-delta*7*limitar(fator,0.5,3)),-960,960);
}
