/*
 * Service worker do AlôSeguro (PWA).
 *
 * Estratégia "rede primeiro": sempre busca a versão mais nova no servidor; se estiver sem
 * internet, usa a cópia guardada das páginas. A API nunca é guardada em cache, porque o
 * risco de um número precisa estar sempre atualizado.
 */
const CACHE = 'aloseguro-v2';
const ARQUIVOS = [
    'index.html', 'como-funciona.html', 'acessibilidade.html', 'offline.html',
    'css/estilo.css', 'js/app.js', 'js/consulta.js',
    'icones/icone-192.png', 'icones/icone-512.png', 'manifest.json'
];

self.addEventListener('install', evento => {
    evento.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ARQUIVOS)));
    self.skipWaiting();
});

self.addEventListener('activate', evento => {
    evento.waitUntil(
        caches.keys().then(chaves => Promise.all(chaves.filter(c => c !== CACHE).map(c => caches.delete(c))))
    );
    self.clients.claim();
});

self.addEventListener('fetch', evento => {
    const url = new URL(evento.request.url);
    if (evento.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) {
        return; // deixa o navegador tratar normalmente
    }
    evento.respondWith(
        fetch(evento.request)
            .then(resposta => {
                const copia = resposta.clone();
                caches.open(CACHE).then(cache => cache.put(evento.request, copia));
                return resposta;
            })
            .catch(() => caches.match(evento.request)
                .then(guardado => guardado || (evento.request.mode === 'navigate' ? caches.match('offline.html') : undefined)))
    );
});
