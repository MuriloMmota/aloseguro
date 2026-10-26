/*
 * Funções compartilhadas por todas as páginas do AlôSeguro:
 * guarda o token JWT, monta o menu e faz as chamadas à API.
 */

const API = '/api';

const Sessao = {
    salvar(resposta) {
        try {
            localStorage.setItem('aloseguro_token', resposta.token);
            localStorage.setItem('aloseguro_nome', resposta.nome);
        } catch (e) { /* navegador sem localStorage: segue sem lembrar o login */ }
    },
    token() {
        try { return localStorage.getItem('aloseguro_token'); } catch (e) { return null; }
    },
    nome() {
        try { return localStorage.getItem('aloseguro_nome'); } catch (e) { return null; }
    },
    sair() {
        try {
            localStorage.removeItem('aloseguro_token');
            localStorage.removeItem('aloseguro_nome');
        } catch (e) { /* nada a fazer */ }
    },
    logado() {
        return !!this.token();
    }
};

/**
 * Faz uma requisição à API. Envia o token automaticamente quando existe.
 * Se a API responder 401 numa rota protegida, encerra a sessão e manda para o login.
 */
async function chamarApi(caminho, opcoes = {}) {
    const cabecalhos = { 'Content-Type': 'application/json', ...(opcoes.headers || {}) };
    const token = Sessao.token();
    if (token) cabecalhos['Authorization'] = 'Bearer ' + token;

    const resposta = await fetch(API + caminho, { ...opcoes, headers: cabecalhos });
    let corpo = null;
    try { corpo = await resposta.json(); } catch (e) { /* resposta sem corpo */ }

    if (resposta.status === 401 && opcoes.protegida) {
        Sessao.sair();
        irParaLogin();
        throw new Error('Sessão expirada.');
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

/** Páginas protegidas chamam isto logo no início. */
function exigirLogin() {
    if (!Sessao.logado()) {
        irParaLogin();
        return false;
    }
    return true;
}

function montarTopo() {
    const topo = document.getElementById('topo');
    if (!topo) return;

    const pagina = location.pathname.split('/').pop() || 'index.html';
    const link = (href, texto) =>
        `<a href="${href}" class="${pagina === href ? 'ativo' : ''}">${texto}</a>`;

    const conta = Sessao.logado()
        ? `<span class="usuario">Olá, ${escapar(primeiroNome(Sessao.nome()))}</span>
           <button type="button" id="botao-sair">Sair</button>`
        : link('entrar.html', 'Entrar');

    topo.className = 'topo';
    topo.innerHTML = `
        <div class="topo-conteudo">
            <a class="logo" href="index.html">Alô<span>Seguro</span></a>
            <nav class="menu" aria-label="Menu principal">
                ${link('index.html', 'Consultar')}
                ${link('denunciar.html', 'Denunciar')}
                ${link('ranking.html', 'Mais denunciados')}
                ${link('minhas.html', 'Minhas denúncias')}
                ${conta}
            </nav>
        </div>`;

    const sair = document.getElementById('botao-sair');
    if (sair) {
        sair.addEventListener('click', () => {
            Sessao.sair();
            location.href = 'index.html';
        });
    }
}

function mostrarErro(elemento, erro) {
    const detalhes = (erro.detalhes || []).map(d => `<li>${escapar(d)}</li>`).join('');
    elemento.className = 'aviso erro';
    elemento.innerHTML = escapar(erro.message) + (detalhes ? `<ul>${detalhes}</ul>` : '');
    elemento.hidden = false;
}

function mostrarSucesso(elemento, texto) {
    elemento.className = 'aviso sucesso';
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

function parametro(nome) {
    return new URLSearchParams(location.search).get(nome);
}

document.addEventListener('DOMContentLoaded', montarTopo);
