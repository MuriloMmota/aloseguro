/*
 * Funções compartilhadas por todas as páginas do AlôSeguro:
 * sessão (token JWT), chamadas à API, menu, barra de acessibilidade, VLibras e PWA.
 */

const API = '/api';

// ------------------------------------------------------------------ preferências salvas no navegador
const Preferencias = {
    ler(chave) {
        try { return localStorage.getItem('aloseguro_' + chave); } catch (e) { return null; }
    },
    gravar(chave, valor) {
        try { localStorage.setItem('aloseguro_' + chave, valor); } catch (e) { /* navegador sem armazenamento */ }
    }
};

// Aplica fonte e contraste antes de desenhar o resto da página
(function aplicarPreferencias() {
    const nivelFonte = parseInt(Preferencias.ler('fonte') || '0', 10);
    if (nivelFonte > 0) document.documentElement.classList.add('fonte-' + nivelFonte);
    if (Preferencias.ler('contraste') === '1') document.documentElement.classList.add('alto-contraste');
})();

// ------------------------------------------------------------------ sessão
const Sessao = {
    salvar(resposta) {
        Preferencias.gravar('token', resposta.token);
        Preferencias.gravar('nome', resposta.nome);
    },
    token() { return Preferencias.ler('token'); },
    nome() { return Preferencias.ler('nome'); },
    sair() {
        try {
            localStorage.removeItem('aloseguro_token');
            localStorage.removeItem('aloseguro_nome');
        } catch (e) { /* nada a fazer */ }
    },
    logado() { return !!this.token(); }
};

/**
 * Faz uma requisição à API e envia o token quando existe.
 * Em rota protegida, um 401 encerra a sessão e leva ao login.
 */
async function chamarApi(caminho, opcoes = {}) {
    const cabecalhos = { 'Content-Type': 'application/json', ...(opcoes.headers || {}) };
    const token = Sessao.token();
    if (token) cabecalhos['Authorization'] = 'Bearer ' + token;

    let resposta;
    try {
        resposta = await fetch(API + caminho, { ...opcoes, headers: cabecalhos });
    } catch (e) {
        throw new Error('Sem conexão com o servidor. Verifique a internet e tente de novo.');
    }

    let corpo = null;
    if (resposta.status !== 204) {
        try { corpo = await resposta.json(); } catch (e) { /* resposta sem corpo */ }
    }

    if (resposta.status === 401 && opcoes.protegida) {
        Sessao.sair();
        irParaLogin();
        throw new Error('Sua sessão expirou. Entre novamente.');
    }
    if (!resposta.ok) {
        const erro = new Error((corpo && corpo.mensagem) || 'Não foi possível completar a operação.');
        erro.detalhes = (corpo && corpo.detalhes) || [];
        erro.status = resposta.status;
        throw erro;
    }
    return corpo;
}

function irParaLogin() {
    const atual = location.pathname.replace(/^\//, '') || 'index.html';
    location.href = 'entrar.html?voltar=' + encodeURIComponent(atual + location.search);
}

function exigirLogin() {
    if (!Sessao.logado()) {
        irParaLogin();
        return false;
    }
    return true;
}

// ------------------------------------------------------------------ estrutura comum das páginas
function montarEstrutura() {
    const pagina = location.pathname.split('/').pop() || 'index.html';

    // link "pular para o conteúdo" + barra de acessibilidade
    const barra = document.createElement('div');
    barra.innerHTML = `
        <a class="pular-conteudo" href="#conteudo">Pular para o conteúdo</a>
        <div class="barra-acessibilidade" role="region" aria-label="Opções de acessibilidade">
            <div class="conteudo">
                <span>Acessibilidade:</span>
                <button type="button" id="fonte-menos" aria-label="Diminuir o tamanho do texto">A−</button>
                <button type="button" id="fonte-mais" aria-label="Aumentar o tamanho do texto">A+</button>
                <button type="button" id="contraste" aria-pressed="false">Alto contraste</button>
                <a href="acessibilidade.html">Sobre</a>
            </div>
        </div>`;
    document.body.prepend(barra);

    // topo com menu
    const topo = document.getElementById('topo');
    if (topo) {
        const link = (href, texto) =>
            `<a href="${href}"${pagina === href ? ' aria-current="page"' : ''}>${texto}</a>`;
        const conta = Sessao.logado()
            ? `<span class="usuario">Olá, ${escapar(primeiroNome(Sessao.nome()))}</span>
               <button type="button" id="botao-sair">Sair</button>`
            : link('entrar.html', 'Entrar');

        topo.className = 'topo';
        topo.innerHTML = `
            <div class="topo-conteudo">
                <a class="logo" href="index.html" aria-label="AlôSeguro, página inicial">
                    <img src="icones/icone-192.png" alt="" width="30" height="30"><span class="marca">Alô<span>Seguro</span></span>
                </a>
                <button type="button" class="botao-menu" id="botao-menu" aria-expanded="false" aria-controls="menu-principal">
                    ☰<span class="sr-only">Abrir menu</span>
                </button>
                <nav class="menu" id="menu-principal" aria-label="Menu principal">
                    ${link('index.html', 'Consultar')}
                    ${link('denunciar.html', 'Denunciar')}
                    ${link('ranking.html', 'Mais denunciados')}
                    ${link('minhas.html', 'Minhas denúncias')}
                    ${link('como-funciona.html', 'Como funciona')}
                    ${conta}
                </nav>
            </div>`;

        const botaoMenu = document.getElementById('botao-menu');
        const menu = document.getElementById('menu-principal');
        botaoMenu.addEventListener('click', () => {
            const aberto = menu.classList.toggle('aberto');
            botaoMenu.setAttribute('aria-expanded', String(aberto));
        });

        const sair = document.getElementById('botao-sair');
        if (sair) {
            sair.addEventListener('click', () => {
                Sessao.sair();
                location.href = 'index.html';
            });
        }
    }

    const rodape = document.getElementById('rodape');
    if (rodape) {
        rodape.className = 'rodape-site';
        rodape.innerHTML = `
            <p>AlôSeguro — Projeto A3 de Sistemas Distribuídos e Mobile · Universidade Anhembi Morumbi</p>
            <p><a href="como-funciona.html">Como calculamos o risco</a> · <a href="acessibilidade.html">Acessibilidade</a></p>
            <p>Os números e bancos exibidos são fictícios, criados para demonstração.</p>`;
    }

    configurarAcessibilidade();
    carregarVLibras();
}

function configurarAcessibilidade() {
    const raiz = document.documentElement;
    let nivel = parseInt(Preferencias.ler('fonte') || '0', 10);
    const aplicarFonte = () => {
        raiz.classList.remove('fonte-1', 'fonte-2', 'fonte-3');
        if (nivel > 0) raiz.classList.add('fonte-' + nivel);
        Preferencias.gravar('fonte', String(nivel));
    };
    document.getElementById('fonte-mais').addEventListener('click', () => { nivel = Math.min(3, nivel + 1); aplicarFonte(); });
    document.getElementById('fonte-menos').addEventListener('click', () => { nivel = Math.max(0, nivel - 1); aplicarFonte(); });

    const botaoContraste = document.getElementById('contraste');
    botaoContraste.setAttribute('aria-pressed', String(raiz.classList.contains('alto-contraste')));
    botaoContraste.addEventListener('click', () => {
        const ativo = raiz.classList.toggle('alto-contraste');
        botaoContraste.setAttribute('aria-pressed', String(ativo));
        Preferencias.gravar('contraste', ativo ? '1' : '0');
    });
}

/** Widget oficial do governo federal que traduz o conteúdo da página para Libras. */
function carregarVLibras() {
    if (!navigator.onLine) return;
    const area = document.createElement('div');
    area.setAttribute('vw', '');
    area.className = 'enabled';
    area.innerHTML = '<div vw-access-button class="active"></div>'
        + '<div vw-plugin-wrapper><div class="vw-plugin-top-wrapper"></div></div>';
    document.body.appendChild(area);

    const script = document.createElement('script');
    script.src = 'https://vlibras.gov.br/app/vlibras-plugin.js';
    script.async = true;
    script.onload = () => {
        try { new window.VLibras.Widget('https://vlibras.gov.br/app'); } catch (e) { /* sem VLibras */ }
    };
    document.body.appendChild(script);
}

// ------------------------------------------------------------------ mensagens e utilidades
function mostrarErro(elemento, erro) {
    const detalhes = (erro.detalhes || []).map(d => `<li>${escapar(d)}</li>`).join('');
    elemento.className = 'aviso erro';
    elemento.setAttribute('role', 'alert');
    elemento.innerHTML = escapar(erro.message) + (detalhes ? `<ul>${detalhes}</ul>` : '');
    elemento.hidden = false;
}

function mostrarSucesso(elemento, texto) {
    elemento.className = 'aviso sucesso';
    elemento.setAttribute('role', 'status');
    elemento.textContent = texto;
    elemento.hidden = false;
}

function escapar(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML;
}

function primeiroNome(nome) {
    return (nome || '').split(' ')[0];
}

function formatarDataHora(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function tempoRelativo(iso) {
    if (!iso) return '';
    const minutos = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    if (minutos < 60) return `há ${Math.max(1, minutos)} min`;
    const horas = Math.round(minutos / 60);
    if (horas < 24) return `há ${horas} h`;
    const dias = Math.round(horas / 24);
    return dias === 1 ? 'há 1 dia' : `há ${dias} dias`;
}

function parametro(nome) {
    return new URLSearchParams(location.search).get(nome);
}

const NOMES_NIVEL = {
    SEM_REGISTROS: 'Sem registros',
    BAIXO: 'Risco baixo',
    ATENCAO: 'Atenção',
    ALTO: 'Alto risco',
    CENTRAL_OFICIAL: 'Central oficial — cuidado'
};

const ICONES_NIVEL = { SEM_REGISTROS: '○', BAIXO: '✓', ATENCAO: '!', ALTO: '✕', CENTRAL_OFICIAL: 'ⓘ' };

// ------------------------------------------------------------------ PWA: permite instalar o site no celular
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js').catch(() => { /* sem PWA, o site funciona igual */ });
    });
}

document.addEventListener('DOMContentLoaded', montarEstrutura);
