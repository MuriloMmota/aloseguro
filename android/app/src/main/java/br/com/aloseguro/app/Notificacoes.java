package br.com.aloseguro.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class Notificacoes {

    private static final String CANAL = "alertas_chamadas";

    private Notificacoes() {
    }

    public static void criarCanal(Context contexto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL, "Alertas de chamadas", NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisa o risco de golpe quando você recebe uma ligação");
            contexto.getSystemService(NotificationManager.class).createNotificationChannel(canal);
        }
    }

    public static boolean permitidas(Context contexto) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Mostra o resultado da consulta como notificação durante a chamada. */
    @SuppressLint("MissingPermission") // a permissão é verificada em permitidas()
    public static void alertaChamada(Context contexto, Consulta consulta) {
        if (!permitidas(contexto)) {
            return;
        }
        criarCanal(contexto);

        Intent abrir = new Intent(contexto, MainActivity.class)
                .putExtra(MainActivity.EXTRA_NUMERO, consulta.numero)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent acao = PendingIntent.getActivity(contexto, consulta.numero.hashCode(), abrir,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String titulo = consulta.rotuloNivel() + " · " + consulta.numeroFormatado;

        NotificationCompat.Builder notificacao = new NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_alerta)
                .setContentTitle(titulo)
                .setContentText(consulta.mensagem)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(consulta.mensagem))
                .setColor(consulta.corNivel())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(acao);

        NotificationManagerCompat.from(contexto).notify(consulta.numero.hashCode(), notificacao.build());
    }
}
