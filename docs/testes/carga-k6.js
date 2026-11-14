/*
 * Teste de carga do AlôSeguro publicado na nuvem (teste não funcional de desempenho).
 *
 * Como rodar:
 *   1. Instale o k6: https://grafana.com/docs/k6/latest/set-up/install-k6/
 *      (Windows: winget install k6 --source winget)
 *   2. Abra o site no navegador antes, para o Render "acordar" o servidor
 *   3. No terminal, na pasta do projeto:
 *        k6 run -e BASE_URL=https://SEU-ENDERECO.onrender.com docs/testes/carga-k6.js
 *
 * Cenários:
 *   - normal: 5 usuários consultando por 1 minuto
 *   - pico:   sobe para 30 usuários simultâneos e mantém por 1 minuto
 *
 * Critérios de aceitação (thresholds): menos de 1% de erros e 95% das consultas
 * respondidas em até 1,5 s no uso normal e 3 s no pico (plano gratuito do Render).
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

const NUMEROS = [
    '11900000001', '21900000002', '1130000003', '08007001000', '11977709999', '11912345678',
];

export const options = {
    scenarios: {
        normal: {
            executor: 'constant-vus', vus: 5, duration: '1m',
            tags: { cenario: 'normal' },
        },
        pico: {
            executor: 'ramping-vus', startTime: '1m', startVUs: 5,
            stages: [{ duration: '20s', target: 30 }, { duration: '1m', target: 30 }, { duration: '10s', target: 0 }],
            tags: { cenario: 'pico' },
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{cenario:normal}': ['p(95)<1500'],
        'http_req_duration{cenario:pico}': ['p(95)<3000'],
    },
};

export default function () {
    const numero = NUMEROS[Math.floor(Math.random() * NUMEROS.length)];
    const resposta = http.get(`${BASE}/api/numeros/${numero}`);
    check(resposta, {
        'status 200': r => r.status === 200,
        'tem nível de risco': r => r.json('nivel') !== undefined,
    });
    sleep(1);
}
