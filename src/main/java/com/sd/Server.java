package com.sd;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Server extends Thread {
    private final Socket clientSocket;
    private static final ObjectMapper mapper = new ObjectMapper();

    // Simulações de persistência em memória para a EP1
    // cpf -> { nome, senha, saldo(double como String p/ simplificar), ... }
    private static final Map<String, Map<String, String>> usuarios = new ConcurrentHashMap<>();
    // token -> cpf
    private static final Map<String, String> tokens = new ConcurrentHashMap<>();

    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }

    @Override
    public void run() {
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            boolean conectado = false;
            String input;
            while ((input = in.readLine()) != null) {
                try {
                    // Garante que a mensagem recebida segue o protocolo (CLIENTE->SERVIDOR)
                    Validator.validateClient(input);

                    Map<String, Object> req = mapper.readValue(input, Map.class);
                    String operacao = (String) req.get("operacao");
                    Map<String, Object> resp = new LinkedHashMap<>();

                    // Handshake obrigatório
                    if (!conectado && !"conectar".equals(operacao)) {
                        resp.put("operacao", operacao);
                        resp.put("status", false);
                        resp.put("info", "Erro, para receber uma operacao, a primeira operacao deve ser 'conectar'");
                        // Valida a resposta (SERVIDOR->CLIENTE)
                        Validator.validateServer(mapper.writeValueAsString(resp));
                        out.println(mapper.writeValueAsString(resp));
                        continue;
                    }

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
                            // saldo como string numérica para fácil conversão posterior
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
                            // saldo precisa ser numérico (double) na resposta
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
                            // opcional: invalidar token também
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
                    } else {
                        // Padrão de operação desconhecida
                        resp.put("operacao", operacao);
                        resp.put("status", false);
                        resp.put("info", "Operação desconhecida.");
                    }

                    // Valida a resposta antes de enviar (SERVIDOR->CLIENTE)
                    String jsonResp = mapper.writeValueAsString(resp);
                    try { Validator.validateServer(jsonResp); } catch (Exception e) { e.printStackTrace(); }
                    out.println(jsonResp);

                } catch (Exception e) {
                    // Entrada inválida ou falha interna: responda no envelope esperado
                    Map<String, Object> erro = new LinkedHashMap<>();
                    erro.put("operacao", "conectar");
                    erro.put("status", false);
                    erro.put("info", "Exceção: " + e.getMessage());
                    String jsonErro = mapper.writeValueAsString(erro);
                    try { Validator.validateServer(jsonErro); } catch (Exception ignore) {}
                    out.println(jsonErro);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException ignore) {}
        }
    }

    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Qual porta o servidor deve usar? ");
            int porta = Integer.parseInt(br.readLine());
            try (ServerSocket serverSocket = new ServerSocket(porta)) {
                System.out.println("Servidor rodando na porta " + porta);
                while (true) {
                    Socket s = serverSocket.accept();
                    System.out.println("Cliente conectado: " + s.getRemoteSocketAddress());
                    new Server(s);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
