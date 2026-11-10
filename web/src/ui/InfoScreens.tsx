import { parityTable, supportLabel } from "../parity/parity";

export function SectionTabs({ current }: { current: "ajustes" | "ajuda" }) {
  return (
    <nav className="tabs" aria-label="Seções">
      <a href="#/ajustes" aria-current={current === "ajustes" ? "page" : undefined}>
        Ajustes
      </a>
      <a href="#/ajuda" aria-current={current === "ajuda" ? "page" : undefined}>
        Ajuda
      </a>
    </nav>
  );
}

export function ParityScreen() {
  return (
    <section className="scroll-screen">
      <header className="page-head row-head">
        <a className="back" href="#/ajustes">
          Voltar
        </a>
        <h1>O que este navegador faz</h1>
      </header>
      <p className="lede">
        A tabela abaixo é a paridade deste cliente. Onde o navegador recusa a API, o limite é da plataforma.
      </p>
      <table>
        <thead>
          <tr>
            <th>Capacidade</th>
            <th>Suporte</th>
          </tr>
        </thead>
        <tbody>
          {parityTable.map((row) => (
            <tr key={row.id}>
              <th scope="row">
                {row.label}
                <small>{row.detail}</small>
              </th>
              <td>{supportLabel[row.support]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export function HelpScreen() {
  return (
    <section className="scroll-screen help">
      <header className="page-head row-head">
        <a className="back" href="#/conectar">
          Voltar
        </a>
        <h1>Ajuda</h1>
      </header>
      <SectionTabs current="ajuda" />
      <article>
        <h2>Primeira conexão</h2>
        <p>
          Instale o agente PCFlow no Windows e deixe-o aberto na sua sessão. No navegador, informe o endereço WebSocket
          que o agente mostrar. Não há varredura da rede nem promessa de Bluetooth.
        </p>
        <h2>Gestos</h2>
        <p>
          Arrastar um dedo no touchpad move o cursor. Um toque curto é o clique esquerdo. O trilho rola a janela. Os três
          retângulos são os botões esquerdo, do meio e direito.
        </p>
        <h2>Mídia</h2>
        <p>
          O painel manda reproduzir ou pausar, trocar de faixa, mudar o volume e parar. O comando só sai com a sessão pronta.
        </p>
        <h2>Navegador</h2>
        <p>Há oito ações nomeadas: voltar, avançar, nova aba, fechar, zoom, recarregar e início. Ação sem nome não entra.</p>
        <h2>Aplicativos</h2>
        <p>A lista de janelas vem do agente. Sem sessão, o PCFlow não inventa ícones.</p>
        <h2>Atalhos</h2>
        <p>
          As letras ficam no teclado do sistema e saem em bloco. Fn mostra as teclas especiais. Com o ajuste ligado, um toque
          no modificador envia a tecla, segurar ativa e um segundo toque trava. Se a sessão cair, o cliente solta o que ainda
          estiver preso.
        </p>
        <h2>Área de transferência</h2>
        <p>Este cliente não lê nem escreve a área de transferência. A permissão do navegador não é pedida aqui.</p>
        <h2>Perguntas</h2>
        <p>O navegador encontra o PC sozinho? Não. O endereço é informado por você.</p>
        <h2>Contato</h2>
        <p>Ainda não há canal de contato publicado neste cliente.</p>
      </article>
    </section>
  );
}

export function AccountScreen() {
  return (
    <section className="scroll-screen">
      <header className="page-head row-head">
        <a className="back" href="#/ajustes">
          Voltar
        </a>
        <h1>Conta</h1>
      </header>
      <p className="lede">
        A conta não está configurada. O backend é dependência externa do dono do produto. O controle do PC não usa essa
        conta e continua na rede local.
      </p>
    </section>
  );
}

export function ProScreen() {
  return (
    <section className="scroll-screen">
      <header className="page-head row-head">
        <a className="back" href="#/ajustes">
          Voltar
        </a>
        <h1>PCFlow Pro</h1>
      </header>
      <p className="lede">
        O pagamento não está configurado. O provedor é uma dependência externa do dono do produto. Nada é comprado nesta
        tela.
      </p>
      <ul className="periods">
        <li>
          <span>Mensal</span>
          <small>Valor não configurado</small>
        </li>
        <li>
          <span>Anual</span>
          <small>Valor não configurado</small>
        </li>
        <li>
          <span>Vitalício</span>
          <small>Valor não configurado</small>
        </li>
      </ul>
    </section>
  );
}
