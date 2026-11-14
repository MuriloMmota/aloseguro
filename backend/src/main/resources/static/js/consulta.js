const form = document.getElementById('form-consulta');
const campo = document.getElementById('numero');
const botao = document.getElementById('botao-consultar');
const caixaErro = document.getElementById('erro-consulta');
const resultado = document.getElementById('resultado');

form.addEventListener('submit', evento => {
    evento.preventDefault();
    consultar(campo.value);
});

document.querySelectorAll('.exemplos button').forEach(b =>
    b.addEventListener('click', () => {
        campo.value = b.textContent;
        consultar(b.dataset.numero);
    }));

async function consultar(texto) {
    const digitos = (texto || '').replace(/\D/g, '');
    caixaErro.hidden = true;
    campo.removeAttribute('aria-invalid');
    if (!digitos) {
        campo.setAttribute('aria-invalid', 'true');
        mostrarErro(caixaErro, new Error('Digite um número de telefone.'));
        campo.focus();
        return;
    }

    botao.disabled = true;
    botao.textContent = 'Consultando...';
    try {
        const dados = await chamarApi('/numeros/' + digitos);
        exibir(dados);
        history.replaceState(null, '', '?numero=' + dados.numero);
    } catch (erro) {
        resultado.hidden = true;
        campo.setAttribute('aria-invalid', 'true');
        mostrarErro(caixaErro, erro);
    } finally {
        botao.disabled = false;
        botao.textContent = 'Consultar';
    }
}

function exibir(d) {
    resultado.className = 'resultado cartao nivel-' + d.nivel;
    campo.value = d.numeroFormatado;

    document.getElementById('r-numero').textContent = d.numeroFormatado;
    document.getElementById('r-tipo').textContent = d.tipoLinhaDescricao + (d.bancoOficial ? ' · ' + d.bancoOficial : '');
    document.getElementById('r-selo').innerHTML =
        `<span aria-hidden="true">${ICONES_NIVEL[d.nivel] || ''}</span>${escapar(NOMES_NIVEL[d.nivel] || d.nivel)}`;
    document.getElementById('r-mensagem').textContent = d.mensagem;

    // placar: para central oficial o importante é o alerta, não a nota
    const placar = document.getElementById('r-placar');
    placar.hidden = d.nivel === 'CENTRAL_OFICIAL';
    document.getElementById('r-score').innerHTML = `${d.score}<small>/100</small>`;
    const medidor = document.getElementById('r-medidor');
    medidor.setAttribute('aria-valuenow', d.score);
    medidor.setAttribute('aria-valuetext', `Score de risco ${d.score} de 100`);
    const barra = document.getElementById('r-preenchido');
    barra.style.width = '0';
    requestAnimationFrame(() => { barra.style.width = d.score + '%'; });
    const nomesConfianca = { BAIXA: 'baixa', MEDIA: 'média', ALTA: 'alta' };
    document.getElementById('r-confianca').textContent = d.totalDenuncias > 0
        ? `Confiança da análise: ${nomesConfianca[d.confianca] || d.confianca} (baseada em ${d.totalDenuncias} ${d.totalDenuncias === 1 ? 'denúncia' : 'denúncias'})`
        : 'Sem denúncias para analisar';

    document.getElementById('r-total').textContent = d.totalDenuncias;
    document.getElementById('r-30d').textContent = d.denunciasUltimos30Dias;
    document.getElementById('r-pessoas').textContent = d.pessoasQueDenunciaram;

    document.getElementById('r-fatores').innerHTML = d.fatores.map(f => `
        <li class="${f.pontos === 0 ? 'zerado' : ''}">
            <span class="nome">${escapar(f.nome)}</span>
            <span class="pontos">+${f.pontos} <small>de ${f.maximo}</small></span>
            <div class="barra" aria-hidden="true"><div class="preenchido" style="width:${Math.round(100 * f.pontos / f.maximo)}%"></div></div>
            <span class="explicacao">${escapar(f.explicacao)}</span>
        </li>`).join('');

    document.getElementById('r-topicos').innerHTML = d.topicos.length
        ? d.topicos.map(t => `
            <li class="etiqueta ${t.gravidade >= 1 ? 'grave' : ''}">
                ${escapar(t.descricao)} <span class="qtd" aria-label="em ${t.quantidade} relatos">${t.quantidade}</span>
            </li>`).join('')
        : '<li class="ajuda">Nenhum relato ainda.</li>';

    document.getElementById('r-relatos').innerHTML = d.relatosRecentes.length
        ? d.relatosRecentes.map(r => `
            <li>
                <blockquote>"${escapar(r.descricao)}"</blockquote>
                <div class="meta">${escapar(tempoRelativo(r.registradoEm))} · ${escapar(r.tags.join(', '))}</div>
            </li>`).join('')
        : '<li class="ajuda">Nenhum relato escrito para este número.</li>';

    document.getElementById('r-denunciar').href = 'denunciar.html?numero=' + d.numero;

    resultado.hidden = false;
    resultado.focus({ preventScroll: true });
    resultado.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function carregarNumeros() {
    try {
        const s = await chamarApi('/status');
        const fmt = n => n.toLocaleString('pt-BR');
        document.getElementById('numeros-hero').innerHTML = `
            <div><strong>${fmt(s.numerosCadastrados)}</strong><span>números analisados</span></div>
            <div><strong>${fmt(s.denunciasRegistradas)}</strong><span>denúncias registradas</span></div>
            <div><strong>${s.iaAtiva ? 'IA ativa' : 'Regras'}</strong><span>classificação dos relatos</span></div>`;
    } catch (e) { /* o contador é só informativo */ }
}

carregarNumeros();

// Permite abrir a página já com um número: index.html?numero=11900000001
const inicial = parametro('numero');
if (inicial) {
    campo.value = inicial;
    consultar(inicial);
}
