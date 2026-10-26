const NOMES_NIVEL = {
    SEM_REGISTROS: 'Sem registros',
    BAIXO: 'Risco baixo',
    ATENCAO: 'Atenção',
    ALTO: 'Alto risco',
    CENTRAL_OFICIAL: 'Central oficial — cuidado'
};

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
    if (!digitos) {
        mostrarErro(caixaErro, new Error('Digite um número de telefone.'));
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
    document.getElementById('r-tipo').textContent = d.tipoLinhaDescricao
        + (d.bancoOficial ? ' · ' + d.bancoOficial : '');
    document.getElementById('r-selo').textContent = NOMES_NIVEL[d.nivel] || d.nivel;
    document.getElementById('r-mensagem').textContent = d.mensagem;

    // A barra de score não faz sentido para central oficial nem para número sem registros
    const temScore = d.nivel !== 'CENTRAL_OFICIAL' && d.nivel !== 'SEM_REGISTROS';
    document.getElementById('r-barra').hidden = !temScore;
    document.getElementById('r-score').textContent = d.score + ' / 100';
    const barra = document.getElementById('r-preenchido');
    barra.style.width = '0';
    requestAnimationFrame(() => { barra.style.width = d.score + '%'; });

    document.getElementById('r-motivos').innerHTML =
        d.motivos.map(m => `<li>${escapar(m)}</li>`).join('');

    document.getElementById('r-total').textContent = d.totalDenuncias;
    document.getElementById('r-30d').textContent = d.denunciasUltimos30Dias;
    document.getElementById('r-pessoas').textContent = d.pessoasQueDenunciaram;
    document.getElementById('r-denunciar').href = 'denunciar.html?numero=' + d.numero;

    resultado.hidden = false;
    resultado.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// Permite abrir a página já com um número: index.html?numero=11900000001
const inicial = parametro('numero');
if (inicial) {
    campo.value = inicial;
    consultar(inicial);
}
