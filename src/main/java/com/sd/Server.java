package com.sd;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Server extends Thread {
    protected Socket clientSocket;
    private static final Map<String, Map<String, String>> usuarios = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> tokens = Collections.synchronizedMap(new HashMap<>());

    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }

    public void run() {
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            ObjectMapper mapper = new ObjectMapper();
            boolean conectado = false;
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                try {
                    Validator.validateClient(inputLine);
                    Map<String, Object> recebido = mapper.readValue(inputLine, Map.class);
                    String operacao = (String) recebido.get("operacao");
                    Map<String, Object> resposta = new LinkedHashMap<>();

                    if (!conectado && !"conectar".equals(operacao)) {
                        resposta.put("operacao", operacao);
                        resposta.put("status", false);
                        resposta.put("info", "Erro, para receber uma operacao, a primeira operacao deve ser 'conectar'");
                        out.println(mapper.writeValueAsString(resposta));
                        continue;
                    }
                    if ("conectar".equals(operacao)) {
                        conectado = true;
                        resposta.put("operacao", "conectar");
                        resposta.put("status", true);
                        resposta.put("info", "Servidor conectado com sucesso.");
                    } else if ("usuario_criar".equals(operacao)) {
                        String cpf = (String) recebido.get("cpf");
                        if (!usuarios.containsKey(cpf)) {
                            Map<String, String> dados = new HashMap<>();
                            dados.put("nome", (String) recebido.get("nome"));
                            dados.put("senha", (String) recebido.get("senha"));
                            usuarios.put(cpf, dados);
                            resposta.put("operacao", operacao);
                            resposta.put("status", true);
                            resposta.put("info", "Usuário criado com sucesso.");
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Ocorreu um erro ao criar usuário.");
                        }
                    } else if ("usuario_login".equals(operacao)) {
                        String cpf = (String) recebido.get("cpf");
                        String senha = (String) recebido.get("senha");
                        Map<String, String> dados = usuarios.get(cpf);
                        if (dados != null && dados.get("senha").equals(senha)) {
                            String token = UUID.randomUUID().toString();
                            tokens.put(token, cpf);
                            resposta.put("operacao", operacao);
                            resposta.put("token", token);
                            resposta.put("status", true);
                            resposta.put("info", "Login bem-sucedido.");
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Ocorreu um erro ao realizar login.");
                        }
                    } else if ("usuario_ler".equals(operacao)) {
                        String token = (String) recebido.get("token");
                        String cpf = tokens.get(token);
                        Map<String, String> dados = usuarios.get(cpf);
                        if (dados != null) {
                            resposta.put("operacao", operacao);
                            resposta.put("status", true);
                            resposta.put("info", "Dados do usuário recuperados com sucesso.");
                            Map<String, Object> usuario = new LinkedHashMap<>();
                            usuario.put("cpf", cpf);
                            usuario.put("nome", dados.get("nome"));
                            usuario.put("saldo", 0.0);
                            resposta.put("usuario", usuario);
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Erro ao ler dados do usuário.");
                        }
                    } else if ("usuario_atualizar".equals(operacao)) {
                        String token = (String) recebido.get("token");
                        String cpf = tokens.get(token);
                        Map<String, String> dados = usuarios.get(cpf);
                        Map<String, Object> usuario = (Map<String, Object>) recebido.get("usuario");
                        if (dados != null && usuario != null) {
                            if (usuario.containsKey("nome")) dados.put("nome", (String) usuario.get("nome"));
                            if (usuario.containsKey("senha")) dados.put("senha", (String) usuario.get("senha"));
                            resposta.put("operacao", operacao);
                            resposta.put("status", true);
                            resposta.put("info", "Usuário atualizado com sucesso.");
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Erro ao atualizar usuário.");
                        }
                    } else if ("usuario_deletar".equals(operacao)) {
                        String token = (String) recebido.get("token");
                        String cpf = tokens.get(token);
                        if (usuarios.containsKey(cpf)) {
                            usuarios.remove(cpf);
                            resposta.put("operacao", operacao);
                            resposta.put("status", true);
                            resposta.put("info", "Usuário deletado com sucesso.");
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Erro ao deletar usuário.");
                        }
                    } else if ("usuario_logout".equals(operacao)) {
                        String token = (String) recebido.get("token");
                        if (token != null && tokens.containsKey(token)) {
                            tokens.remove(token);
                            resposta.put("operacao", operacao);
                            resposta.put("status", true);
                            resposta.put("info", "Logout realizado com sucesso.");
                        } else {
                            resposta.put("operacao", operacao);
                            resposta.put("status", false);
                            resposta.put("info", "Ocorreu um erro ao realizar logout.");
                        }
                    } else {
                        resposta.put("operacao", operacao);
                        resposta.put("status", false);
                        resposta.put("info", "Operação desconhecida.");
                    }
                    try { Validator.validateServer(mapper.writeValueAsString(resposta)); } catch (Exception e) { e.printStackTrace(); }
                    out.println(mapper.writeValueAsString(resposta));
                } catch (Exception e) {
                    Map<String, Object> erro = new LinkedHashMap<>();
                    erro.put("operacao", "conectar");
                    erro.put("status", false);
                    erro.put("info", "Exceção: " + e.getMessage());
                    out.println(new ObjectMapper().writeValueAsString(erro));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Qual porta o servidor deve usar? ");
        int porta = Integer.parseInt(br.readLine());
        ServerSocket serverSocket = new ServerSocket(porta);
        System.out.println("Servidor rodando na porta " + porta);

        while (true) {
            new Server(serverSocket.accept());
            System.out.println("Cliente conectado!");
        }
    }
}
