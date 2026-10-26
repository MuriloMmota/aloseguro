package br.com.aloseguro.app;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela única do app: consulta manual de um número e ativação da
 * identificação automática de chamadas.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_NUMERO = "numero";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());

    private TextInputEditText campoNumero;
    private MaterialButton botaoConsultar;
    private ProgressBar carregando;
    private TextView textoErro;

    private MaterialCardView cartaoResultado;
    private TextView resNumero, resTipo, resSelo, resScore, resMensagem, resMotivos;
    private TextView resTotal, res30Dias, resPessoas;
    private LinearProgressIndicator barraScore;
    private View grupoScore;

    private TextView statusProtecao, textoServidor;
    private MaterialButton botaoProtecao;

    private final ActivityResultLauncher<Intent> pedirPapel =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    resultado -> atualizarStatusProtecao());

    private final ActivityResultLauncher<String> pedirNotificacao =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    concedida -> atualizarStatusProtecao());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ligarViews();
        Notificacoes.criarCanal(this);

        botaoConsultar.setOnClickListener(v -> consultar());
        campoNumero.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_SEARCH) {
                consultar();
                return true;
            }
            return false;
        });
        botaoProtecao.setOnClickListener(v -> ativarProtecao());
        textoServidor.setOnClickListener(v -> editarServidor());

        atualizarServidor();
        atualizarStatusProtecao();
        consultarSeVeioDaNotificacao(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        consultarSeVeioDaNotificacao(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatusProtecao();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void ligarViews() {
        campoNumero = findViewById(R.id.campo_numero);
        botaoConsultar = findViewById(R.id.botao_consultar);
        carregando = findViewById(R.id.carregando);
        textoErro = findViewById(R.id.texto_erro);

        cartaoResultado = findViewById(R.id.cartao_resultado);
        resNumero = findViewById(R.id.res_numero);
        resTipo = findViewById(R.id.res_tipo);
        resSelo = findViewById(R.id.res_selo);
        resScore = findViewById(R.id.res_score);
        resMensagem = findViewById(R.id.res_mensagem);
        resMotivos = findViewById(R.id.res_motivos);
        resTotal = findViewById(R.id.res_total);
        res30Dias = findViewById(R.id.res_30dias);
        resPessoas = findViewById(R.id.res_pessoas);
        barraScore = findViewById(R.id.barra_score);
        grupoScore = findViewById(R.id.grupo_score);

        statusProtecao = findViewById(R.id.status_protecao);
        botaoProtecao = findViewById(R.id.botao_protecao);
        textoServidor = findViewById(R.id.texto_servidor);
    }

    // ------------------------------------------------------------------ consulta

    private void consultarSeVeioDaNotificacao(Intent intent) {
        String numero = intent == null ? null : intent.getStringExtra(EXTRA_NUMERO);
        if (!TextUtils.isEmpty(numero)) {
            campoNumero.setText(numero);
            consultar();
        }
    }

    private void consultar() {
        String numero = campoNumero.getText() == null ? "" : campoNumero.getText().toString();
        esconderTeclado();
        textoErro.setVisibility(View.GONE);
        carregando.setVisibility(View.VISIBLE);
        botaoConsultar.setEnabled(false);

        executor.execute(() -> {
            try {
                Consulta consulta = new ApiClient(this).consultar(numero);
                principal.post(() -> mostrarResultado(consulta));
            } catch (ApiClient.ApiException e) {
                principal.post(() -> mostrarErro(e.getMessage()));
            }
        });
    }

    private void mostrarResultado(Consulta c) {
        carregando.setVisibility(View.GONE);
        botaoConsultar.setEnabled(true);

        int cor = c.corNivel();
        cartaoResultado.setStrokeColor(cor);
        resNumero.setText(c.numeroFormatado);
        resTipo.setText(c.bancoOficial == null
                ? c.tipoLinhaDescricao
                : c.tipoLinhaDescricao + " · " + c.bancoOficial);
        resSelo.setText(c.rotuloNivel().toUpperCase());
        resSelo.setBackgroundTintList(ColorStateList.valueOf(cor));

        grupoScore.setVisibility(c.temScore() ? View.VISIBLE : View.GONE);
        barraScore.setIndicatorColor(cor);
        barraScore.setProgressCompat(c.score, true);
        resScore.setText(getString(R.string.score_valor, c.score));

        resMensagem.setText(c.mensagem);
        resMotivos.setText("• " + TextUtils.join("\n• ", c.motivos));
        resTotal.setText(String.valueOf(c.totalDenuncias));
        res30Dias.setText(String.valueOf(c.denunciasUltimos30Dias));
        resPessoas.setText(String.valueOf(c.pessoasQueDenunciaram));

        cartaoResultado.setVisibility(View.VISIBLE);
    }

    private void mostrarErro(String mensagem) {
        carregando.setVisibility(View.GONE);
        botaoConsultar.setEnabled(true);
        cartaoResultado.setVisibility(View.GONE);
        textoErro.setText(mensagem);
        textoErro.setVisibility(View.VISIBLE);
    }

    private void esconderTeclado() {
        InputMethodManager teclado = getSystemService(InputMethodManager.class);
        if (teclado != null) {
            teclado.hideSoftInputFromWindow(campoNumero.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------------ identificação de chamadas

    private void ativarProtecao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificacoes.permitidas(this)) {
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, R.string.protecao_versao, Toast.LENGTH_LONG).show();
            return;
        }
        RoleManager papeis = getSystemService(RoleManager.class);
        if (papeis.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.protecao_ja_ativa, Toast.LENGTH_SHORT).show();
            return;
        }
        pedirPapel.launch(papeis.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING));
    }

    private void atualizarStatusProtecao() {
        boolean ativa = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        if (ativa && Notificacoes.permitidas(this)) {
            statusProtecao.setText(R.string.protecao_ativa);
            botaoProtecao.setVisibility(View.GONE);
        } else if (ativa) {
            statusProtecao.setText(R.string.protecao_sem_notificacao);
            botaoProtecao.setText(R.string.permitir_notificacoes);
            botaoProtecao.setVisibility(View.VISIBLE);
        } else {
            statusProtecao.setText(R.string.protecao_inativa);
            botaoProtecao.setText(R.string.ativar_protecao);
            botaoProtecao.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------ servidor

    private void atualizarServidor() {
        textoServidor.setText(getString(R.string.servidor_atual, ApiClient.servidor(this)));
    }

    private void editarServidor() {
        EditText campo = new EditText(this);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        campo.setText(ApiClient.servidor(this));
        int margem = (int) (20 * getResources().getDisplayMetrics().density);
        campo.setPadding(margem, margem / 2, margem, margem / 2);

        new AlertDialog.Builder(this)
                .setTitle(R.string.servidor_titulo)
                .setMessage(R.string.servidor_ajuda)
                .setView(campo)
                .setPositiveButton(R.string.salvar, (d, i) -> {
                    ApiClient.salvarServidor(this, campo.getText().toString());
                    atualizarServidor();
                })
                .setNeutralButton(R.string.padrao_emulador, (d, i) -> {
                    ApiClient.salvarServidor(this, ApiClient.SERVIDOR_PADRAO);
                    atualizarServidor();
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }
}
