package br.com.aloseguro.app;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da consulta de um número, como devolvido por GET /api/numeros/{numero}.
 */
public class Consulta {

    public String numero;
    public String numeroFormatado;
    public String tipoLinhaDescricao;
    public String nivel;
    public int score;
    public String mensagem;
    public List<String> motivos = new ArrayList<>();
    public long totalDenuncias;
    public long denunciasUltimos30Dias;
    public long pessoasQueDenunciaram;
    public String bancoOficial;

    public static Consulta de(JSONObject json) {
        Consulta c = new Consulta();
        c.numero = json.optString("numero");
        c.numeroFormatado = json.optString("numeroFormatado");
        c.tipoLinhaDescricao = json.optString("tipoLinhaDescricao");
        c.nivel = json.optString("nivel");
        c.score = json.optInt("score");
        c.mensagem = json.optString("mensagem");
        c.totalDenuncias = json.optLong("totalDenuncias");
        c.denunciasUltimos30Dias = json.optLong("denunciasUltimos30Dias");
        c.pessoasQueDenunciaram = json.optLong("pessoasQueDenunciaram");
        c.bancoOficial = json.isNull("bancoOficial") ? null : json.optString("bancoOficial");

        JSONArray lista = json.optJSONArray("motivos");
        if (lista != null) {
            for (int i = 0; i < lista.length(); i++) {
                c.motivos.add(lista.optString(i));
            }
        }
        return c;
    }

    public boolean temScore() {
        return !"CENTRAL_OFICIAL".equals(nivel) && !"SEM_REGISTROS".equals(nivel);
    }

    public String rotuloNivel() {
        switch (nivel) {
            case "ALTO": return "Alto risco";
            case "ATENCAO": return "Atenção";
            case "BAIXO": return "Risco baixo";
            case "CENTRAL_OFICIAL": return "Central oficial — cuidado";
            default: return "Sem registros";
        }
    }

    public int corNivel() {
        switch (nivel) {
            case "ALTO": return Color.parseColor("#D6453D");
            case "ATENCAO": return Color.parseColor("#D98A00");
            case "BAIXO": return Color.parseColor("#1F9D55");
            case "CENTRAL_OFICIAL": return Color.parseColor("#2F6FD6");
            default: return Color.parseColor("#64748B");
        }
    }
}
