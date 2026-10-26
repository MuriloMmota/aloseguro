# AlôSeguro — App Android

App em Java que consome a mesma API REST do site. Duas funções:

1. **Consulta manual**: digita o número e vê o score de risco
2. **Identificação de chamadas**: quando chega uma ligação de um número fora dos contatos, o app consulta a API e mostra uma notificação com o risco — sem bloquear a ligação

## Como rodar

1. Deixe a **API rodando** no IntelliJ (veja o README principal)
2. No Android Studio: **File → Open** e escolha a pasta `android`
3. Aguarde o *Gradle Sync* terminar (na primeira vez demora alguns minutos)
4. Crie um emulador em **Device Manager → Create Virtual Device** (um Pixel com Android 14 serve)
5. Clique em **Run ▶**

O app já vem configurado para `http://10.0.2.2:8080`, que é o endereço do computador visto de dentro do emulador.

### Celular de verdade

1. Computador e celular na mesma rede Wi-Fi
2. Descubra o IP do computador (Windows: `ipconfig`, procure "Endereço IPv4")
3. No app, toque em **Servidor** no rodapé e coloque `http://SEU_IP:8080`
4. Se não conectar, libere a porta 8080 no firewall do Windows

## Testar a identificação de chamadas no emulador

1. No app, toque em **Ativar identificação de chamadas** e aceite as duas permissões
2. No emulador, abra **⋮ (Extended Controls) → Phone**
3. Em *From*, digite `11900000001` e clique em **Call Device**
4. A notificação **ALTO RISCO** aparece junto com a ligação. Tocar nela abre o app com o resultado completo

Outros números para testar: `21900000002` (Atenção), `08007001000` (Central oficial), `11912345678` (Sem registros).

## Limitações conhecidas

- A identificação automática exige **Android 10 ou superior** (papel `ROLE_CALL_SCREENING`)
- O Android só entrega o número de ligações de quem **não está nos contatos**
- No iOS esse recurso não existe da mesma forma: lá só é possível com uma lista de números pré-carregada no aparelho
- O app usa HTTP sem criptografia para falar com a API local; publicada em nuvem, a API usaria HTTPS
