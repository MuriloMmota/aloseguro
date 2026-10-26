package br.com.aloseguro.app;

import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * O Android chama este serviço a cada ligação recebida, antes de o telefone tocar,
 * desde que o AlôSeguro tenha o papel de "app de identificação de chamadas".
 *
 * O app NÃO bloqueia a ligação: ele deixa tocar normalmente e, em paralelo,
 * consulta a API e mostra uma notificação com o risco do número.
 *
 * Limitação do Android: o número só é entregue para ligações de quem não está
 * nos contatos do usuário.
 */
public class AlertaChamadaService extends CallScreeningService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onScreenCall(Call.Details detalhes) {
        // Responde imediatamente liberando a chamada: o Android exige resposta rápida
        respondToCall(detalhes, new CallResponse.Builder().build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && detalhes.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
            return;
        }

        Uri endereco = detalhes.getHandle();
        if (endereco == null) {
            return;
        }
        String numero = endereco.getSchemeSpecificPart();

        executor.execute(() -> {
            try {
                Consulta consulta = new ApiClient(getApplicationContext()).consultar(numero);
                Notificacoes.alertaChamada(getApplicationContext(), consulta);
            } catch (ApiClient.ApiException e) {
                // Sem conexão ou número em formato não reconhecido: não interrompe a ligação
            }
        });
    }
}
