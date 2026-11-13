// Histórico de enquadramentos local: nenhuma ação é enviada ao Windows.
export function criarHistoricoEnquadramento(limite=12){
 const anteriores=[],posteriores=[];
 const valido=s=>s&&[s.zoom,s.panX,s.panY].every(Number.isFinite)&&s.zoom>=1&&s.zoom<=5&&["fit","fill"].includes(s.fillMode);
 const copiar=s=>({zoom:s.zoom,panX:s.panX,panY:s.panY,fillMode:s.fillMode});
 const igual=(a,b)=>a.zoom===b.zoom&&a.panX===b.panX&&a.panY===b.panY&&a.fillMode===b.fillMode;
 return {
  registrar(antes,depois){if(!valido(antes)||!valido(depois)||igual(antes,depois))return false;anteriores.push(copiar(antes));if(anteriores.length>limite)anteriores.shift();posteriores.length=0;return true},
  desfazer(atual){if(!valido(atual)||!anteriores.length)return null;posteriores.push(copiar(atual));return anteriores.pop()},
  refazer(atual){if(!valido(atual)||!posteriores.length)return null;anteriores.push(copiar(atual));return posteriores.pop()},
  limpar(){anteriores.length=0;posteriores.length=0},
  estado(){return {desfazer:anteriores.length>0,refazer:posteriores.length>0}}
 };
}
