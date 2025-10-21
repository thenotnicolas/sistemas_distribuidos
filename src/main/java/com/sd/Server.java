package com.sd;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Server extends Thread {
    private final Socket clientSocket;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Persistência em memória (EP1)
    private static final Map<String, Map<String, String>> usuarios = new ConcurrentHashMap<>();
    private static final Map<String, String> tokens = new ConcurrentHashMap<>();

    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }

    private static String ts() { return "[" + LocalDateTime.now().format(TS) + "]"; }

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

                    // Validação de entrada (CLIENTE->SERVIDOR)
                    try {
                        Validator.validateClient(input);
                        System.out.println(ts() + " [OK  ] Validator.validateClient passed");
                    } catch (Exception ve) {
                        System.out.println(ts() + " [FAIL] validateClient: " + ve.getMessage());
                        // Erros de JSON/estrutura → protocolo exige "null" e fechar
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
                            System.out.println(ts() + " [INFO] Criado usuario cpf=" + cpf + " dados=" + dados);
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
                            System.out.println(ts() + " [INFO] Login ok cpf=" + cpf + " token=" + token);
                            resp.put("operacao", operacao);
                            resp.put("token", token);
                            resp.put("status", true);
                            resp.put("info", "Login bem-sucedido.");
                        } else {
                            System.out.println(ts() + " [INFO] Login falhou cpf=" + cpf);
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

                            System.out.println(ts() + " [INFO] Ler usuario cpf=" + cpf + " -> " + usuario);
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
                            // Loga dados antigos
                            System.out.println(ts() + " [INFO] Atualização - cpf: " + cpf + " | Antes: " + dados);

                            if (usuarioReq.containsKey("nome")) {
                                String n = ((String) usuarioReq.get("nome")).trim();
                                if (!n.isBlank()) dados.put("nome", n);
                            }
                            if (usuarioReq.containsKey("senha")) {
                                String s = ((String) usuarioReq.get("senha")).trim();
                                if (!s.isBlank()) dados.put("senha", s);
                            }

                            // Loga dados novos
                            System.out.println(ts() + " [INFO] Atualização - cpf: " + cpf + " | Depois: " + dados);

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
                            System.out.println(ts() + " [INFO] Deletando usuario cpf=" + cpf);
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
                            String cpf = tokens.get(token);
                            tokens.remove(token);
                            System.out.println(ts() + " [INFO] Logout token=" + token + " cpf=" + cpf);
                            resp.put("operacao", operacao);
                            resp.put("status", true);
                            resp.put("info", "Logout realizado com sucesso.");
                        } else {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Ocorreu um erro ao realizar logout.");
                        }

                    } else {
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
