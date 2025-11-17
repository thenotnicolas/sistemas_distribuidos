package com.sd;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Server extends Thread {
    private final Socket clientSocket;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // Usuários e tokens
    private static final Map<String, Map<String, String>> usuarios = new ConcurrentHashMap<>();
    private static final Map<String, String> tokens = new ConcurrentHashMap<>();

    // Histórico de transações (CPF -> lista de transações)
    private static final Map<String, List<Map<String, Object>>> transacoes = new ConcurrentHashMap<>();
    private static final AtomicInteger idTransacao = new AtomicInteger(1);

    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }

    private static String ts() { return "[" + LocalDateTime.now().format(TS) + "]"; }
    private static String isoNow() { return LocalDateTime.now().format(ISO); }

    @Override
    public void run() {
        String who = clientSocket.getRemoteSocketAddress().toString();
        System.out.println(ts() + " [ACCEPT] Cliente conectado: " + who);
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            boolean conectado = false;
            String input;
            while (true) {
                try {
                    input = in.readLine();
                    if (input == null) {
                        System.out.println(ts() + " [EOF] " + who + " encerrou a conexão.");
                        break;
                    }
                    System.out.println(ts() + " [RECV] " + who + " -> " + input);

                    try {
                        Validator.validateClient(input);
                        System.out.println(ts() + " [OK  ] Validator.validateClient passed");
                    } catch (Exception ve) {
                        System.out.println(ts() + " [FAIL] validateClient: " + ve.getMessage());
                        out.println("null");
                        System.out.println(ts() + " [SEND] null (closing) para " + who);
                        break;
                    }

                    Map<String, Object> req = mapper.readValue(input, Map.class);
                    String operacao = (String) req.get("operacao");
                    Map<String, Object> resp = new LinkedHashMap<>();

                    // Handshake obrigatório
                    if (!conectado && !"conectar".equals(operacao)) {
                        resp.put("operacao", operacao);
                        resp.put("status", false);
                        resp.put("info", "Erro, para receber uma operacao, a primeira operacao deve ser 'conectar'");
                        String json = mapper.writeValueAsString(resp);
                        try { Validator.validateServer(json); } catch (Exception e) { System.out.println(ts() + " [WARN] validateServer resp: " + e.getMessage()); }
                        out.println(json);
                        System.out.println(ts() + " [SEND] " + who + " <- " + json);
                        continue;
                    }

                    System.out.println(ts() + " [ROUTE] operacao=" + operacao);

                    if ("conectar".equals(operacao)) {
                        conectado = true;
                        resp.put("operacao", "conectar");
                        resp.put("status", true);
                        resp.put("info", "Servidor conectado com sucesso.");

                    } else if ("usuario_criar".equals(operacao)) {
                        String cpf = ((String) req.get("cpf")).trim();
                        if (!usuarios.containsKey(cpf)) {
                            Map<String, String> dados = new HashMap<>();
                            dados.put("nome", ((String) req.get("nome")).trim());
                            dados.put("senha", ((String) req.get("senha")).trim());
                            dados.put("saldo", "0.0");
                            usuarios.put(cpf, dados);
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Usuário criado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Ocorreu um erro ao criar usuário.");
                        }

                    } else if ("usuario_login".equals(operacao)) {
                        String cpf = ((String) req.get("cpf")).trim();
                        String senha = ((String) req.get("senha")).trim();
                        Map<String, String> dados = usuarios.get(cpf);
                        if (dados != null && Objects.equals(dados.get("senha"), senha)) {
                            String token = UUID.randomUUID().toString();
                            tokens.put(token, cpf);
                            resp.put("operacao", operacao);
                            resp.put("token", token);
                            resp.put("status", true);
                            resp.put("info", "Login bem-sucedido.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Ocorreu um erro ao realizar login.");
                        }

                    } else if ("usuario_ler".equals(operacao)) {
                        String token = ((String) req.get("token"));
                        String cpf = tokens.get(token);
                        Map<String, String> dados = cpf != null ? usuarios.get(cpf) : null;
                        if (dados != null) {
                            Map<String, Object> usuario = new LinkedHashMap<>();
                            usuario.put("cpf", cpf);
                            usuario.put("nome", dados.get("nome"));
                            double saldo = 0.0;
                            try { saldo = Double.parseDouble(dados.getOrDefault("saldo", "0.0")); } catch (Exception ignore) {}
                            usuario.put("saldo", saldo);
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Dados do usuário recuperados com sucesso.");
                            resp.put("usuario", usuario);
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Erro ao ler dados do usuário.");
                        }

                    } else if ("usuario_atualizar".equals(operacao)) {
                        String token = ((String) req.get("token"));
                        String cpf = tokens.get(token);
                        Map<String, String> dados = cpf != null ? usuarios.get(cpf) : null;
                        Map<String, Object> usuarioReq = (Map<String, Object>) req.get("usuario");
                        if (dados != null && usuarioReq != null) {
                            if (usuarioReq.containsKey("nome")) {
                                String n = ((String) usuarioReq.get("nome")).trim();
                                if (!n.isBlank()) dados.put("nome", n);
                            }
                            if (usuarioReq.containsKey("senha")) {
                                String s = ((String) usuarioReq.get("senha")).trim();
                                if (!s.isBlank()) dados.put("senha", s);
                            }
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Usuário atualizado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Erro ao atualizar usuário.");
                        }

                    } else if ("usuario_deletar".equals(operacao)) {
                        String token = ((String) req.get("token"));
                        String cpf = tokens.get(token);
                        if (cpf != null && usuarios.containsKey(cpf)) {
                            usuarios.remove(cpf);
                            tokens.values().removeIf(v -> Objects.equals(v, cpf));
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Usuário deletado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Erro ao deletar usuário.");
                        }

                    } else if ("usuario_logout".equals(operacao)) {
                        String token = ((String) req.get("token"));
                        if (token != null && tokens.containsKey(token)) {
                            tokens.remove(token);
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Logout realizado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Ocorreu um erro ao realizar logout.");
                        }

                    } else if ("depositar".equals(operacao)) {
                        String token = ((String) req.get("token"));
                        Double valor = null;
                        if (req.get("valor_enviado") instanceof Number) {
                            valor = ((Number) req.get("valor_enviado")).doubleValue();
                        } else {
                            valor = Double.parseDouble(req.get("valor_enviado").toString());
                        }
                        String cpf = tokens.get(token);
                        if (cpf != null && usuarios.containsKey(cpf) && valor != null && valor > 0) {
                            Map<String, String> dados = usuarios.get(cpf);
                            double saldoAntigo = Double.parseDouble(dados.getOrDefault("saldo", "0.0"));
                            double saldoNovo = saldoAntigo + valor;
                            dados.put("saldo", String.valueOf(saldoNovo));
                            // Adiciona transação tipo depósito (enviador=recebedor=usuário)
                            Map<String, Object> t = new LinkedHashMap<>();
                            int id = idTransacao.getAndIncrement();
                            t.put("id", id);
                            t.put("valor_enviado", valor);
                            t.put("usuario_enviador", Map.of("nome", dados.get("nome"), "cpf", cpf));
                            t.put("usuario_recebedor", Map.of("nome", dados.get("nome"), "cpf", cpf));
                            t.put("criado_em", isoNow());
                            t.put("atualizado_em", isoNow());
                            transacoes.computeIfAbsent(cpf, k -> new ArrayList<>()).add(t);
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Deposito realizado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Erro ao depositar.");
                        }

                    } else if ("transacao_ler".equals(operacao)) {
                        String token = (String) req.get("token");
                        String cpf = tokens.get(token);
                        String dIni = (String) req.get("data_inicial");
                        String dFim = (String) req.get("data_final");
                        List<Map<String, Object>> extrato = new ArrayList<>();
                        try {
                            LocalDateTime ini = LocalDateTime.parse(dIni.replace("Z", ""));
                            LocalDateTime fim = LocalDateTime.parse(dFim.replace("Z", ""));
                            if (cpf != null && transacoes.containsKey(cpf)) {
                                List<Map<String, Object>> todas = transacoes.getOrDefault(cpf, Collections.emptyList());
                                extrato = todas.stream()
                                        .filter(t -> {
                                            String dt = (String) t.get("criado_em");
                                            LocalDateTime td = LocalDateTime.parse(dt.replace("Z", ""));
                                            return !td.isBefore(ini) && !td.isAfter(fim);
                                        })
                                        .collect(Collectors.toList());
                                resp.put("operacao", operacao);
                                resp.put("status", true);
                                resp.put("info", "Transações recuperadas com sucesso.");
                                resp.put("transacoes", extrato);
                            } else {
                                resp.put("operacao", operacao);
                                resp.put("status", false);
                                resp.put("info", "Erro ao ler transações.");
                            }
                        } catch (Exception ex) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Erro ao ler transações.");
                        }
                    }
                    else {
                        resp.put("operacao", operacao);
                        resp.put("status", false);
                        resp.put("info", "Operação desconhecida.");
                    }

                    String jsonResp = mapper.writeValueAsString(resp);
                    try {
                        Validator.validateServer(jsonResp);
                        System.out.println(ts() + " [OK  ] Validator.validateServer passed");
                    } catch (Exception e) {
                        System.out.println(ts() + " [WARN] validateServer resp: " + e.getMessage());
                    }
                    out.println(jsonResp);
                    System.out.println(ts() + " [SEND] " + who + " <- " + jsonResp);

                } catch (Exception e) {
                    System.out.println(ts() + " [EXCP] " + who + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    out.println("null");
                    System.out.println(ts() + " [SEND] null (closing) para " + who);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(ts() + " [EXCP] Falha I/O com " + who + ": " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException ignore) {}
            System.out.println(ts() + " [CLOSE] Socket fechado: " + who);
        }
    }

    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Qual porta o servidor deve usar? ");
            int porta = Integer.parseInt(br.readLine());
            try (ServerSocket serverSocket = new ServerSocket(porta)) {
                System.out.println(ts() + " [BOOT] Servidor rodando na porta " + porta);
                while (true) {
                    Socket s = serverSocket.accept();
                    new Server(s);
                }
            }
        } catch (Exception e) {
            System.out.println(ts() + " [EXCP] Falha no servidor: " + e.getMessage());
        }
    }
}
