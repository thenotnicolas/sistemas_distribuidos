package com.sd;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Client {
    private static String token = "";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter dtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String serverIP = "";
        int serverPort = 0;

        System.out.println("=== CLIENTE - SISTEMAS DISTRIBUÍDOS ===");
        System.out.print("IP do servidor: ");
        serverIP = br.readLine();
        System.out.print("Porta do servidor: ");
        serverPort = Integer.parseInt(br.readLine());

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            socket = new Socket(serverIP, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("[INFO] Conectado ao servidor em " + serverIP + ":" + serverPort);

            // Loop principal
            while (true) {
                exibirMenu();
                System.out.print("Escolha uma opção: ");
                String op = br.readLine();

                // Verificação de login para ações que exigem token
                if (("3 4 5 6 7 8").contains(op)) {
                    if (token == null || token.isBlank()) {
                        System.out.println("[AVISO] Você precisa estar logado para realizar essa ação.");
                        continue;
                    }
                }

                Map<String, Object> mensagem = new LinkedHashMap<>();
                
                if ("1".equals(op)) {
                    // Criar usuário
                    mensagem.put("operacao", "usuario_criar");
                    System.out.print("Nome (mín 6 caract.): ");
                    mensagem.put("nome", br.readLine());
                    System.out.print("CPF (formato 000.000.000-00): ");
                    mensagem.put("cpf", br.readLine());
                    System.out.print("Senha (mín 6 caract.): ");
                    mensagem.put("senha", br.readLine());
                    
                } else if ("2".equals(op)) {
                    // Login
                    mensagem.put("operacao", "usuario_login");
                    System.out.print("CPF (formato 000.000.000-00): ");
                    mensagem.put("cpf", br.readLine());
                    System.out.print("Senha (mín 6 caract.): ");
                    mensagem.put("senha", br.readLine());
                    
                } else if ("3".equals(op)) {
                    // Ler dados
                    mensagem.put("operacao", "usuario_ler");
                    mensagem.put("token", token);
                    
                } else if ("4".equals(op)) {
                    // Atualizar dados
                    mensagem.put("operacao", "usuario_atualizar");
                    mensagem.put("token", token);
                    Map<String, Object> usuario = new LinkedHashMap<>();
                    System.out.print("Novo nome (mín 6 ou enter p/ manter): ");
                    String nome = br.readLine();
                    System.out.print("Nova senha (mín 6 ou enter p/ manter): ");
                    String senha = br.readLine();
                    if (nome != null && !nome.isBlank()) usuario.put("nome", nome);
                    if (senha != null && !senha.isBlank()) usuario.put("senha", senha);
                    mensagem.put("usuario", usuario);
                    
                } else if ("5".equals(op)) {
                    // Deletar conta
                    mensagem.put("operacao", "usuario_deletar");
                    mensagem.put("token", token);
                    System.out.print("Deseja realmente deletar sua conta? (s/n): ");
                    String confirmacao = br.readLine();
                    if (!confirmacao.equalsIgnoreCase("s")) {
                        System.out.println("[INFO] Operação cancelada.");
                        continue;
                    }
                    
                } else if ("6".equals(op)) {
                    // Logout
                    mensagem.put("operacao", "usuario_logout");
                    mensagem.put("token", token);
                    
                } else if ("7".equals(op)) {
                    // Depositar dinheiro
                    mensagem.put("operacao", "depositar");
                    mensagem.put("token", token);
                    System.out.print("Valor a depositar (use ponto como separador, ex: 99.50): ");
                    String valorStr = br.readLine().replace(",", ".");
                    double val;
                    try {
                        val = Double.parseDouble(valorStr);
                        if (val <= 0) {
                            System.out.println("[ERRO] O valor deve ser maior que zero.");
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("[ERRO] Formato inválido! Use ponto como separador decimal, ex: 52.30");
                        continue;
                    }
                    mensagem.put("valor_enviado", val);
                    
                } else if ("8".equals(op)) {
                    // Solicitar extrato
                    mensagem.put("operacao", "transacao_ler");
                    mensagem.put("token", token);
                    System.out.print("Data inicial (ex: 2025-11-01T00:00:00Z): ");
                    String dIni = br.readLine();
                    System.out.print("Data final (ex: 2025-11-17T23:59:59Z): ");
                    String dFim = br.readLine();
                    mensagem.put("data_inicial", dIni);
                    mensagem.put("data_final", dFim);
                    
                } else if ("0".equals(op)) {
                    System.out.println("[INFO] Cliente finalizado.");
                    break;
                    
                } else {
                    System.out.println("[AVISO] Opção inválida.");
                    continue;
                }

                // Validar e enviar mensagem
                String jsonEnvio = mapper.writeValueAsString(mensagem);
                try {
                    Validator.validateClient(jsonEnvio);
                } catch (Exception e) {
                    System.out.println("[ERRO] Entrada inválida: " + e.getMessage());
                    continue;
                }

                System.out.println("[ENVIADO] " + jsonEnvio);
                out.println(jsonEnvio);

                // Receber resposta
                String respostaServer;
                try {
                    respostaServer = in.readLine();
                } catch (java.net.SocketException se) {
                    System.out.println("[ERRO] Conexão encerrada pelo servidor: " + se.getMessage());
                    break;
                }

                if (respostaServer == null || "null".equals(respostaServer)) {
                    System.out.println("[ERRO] Servidor encerrou a conexão (mensagem inválida/protocolo). Reinicie o client.");
                    break;
                }

                // Validar resposta do servidor
                try {
                    Validator.validateServer(respostaServer);
                } catch (Exception e) {
                    System.out.println("[ERRO] Resposta inválida do servidor: " + e.getMessage());
                    break;
                }

                System.out.println("[RECEBIDO] " + respostaServer);

                // Processar resposta
                try {
                    Map<String, Object> respMap = mapper.readValue(respostaServer, Map.class);
                    boolean status = (Boolean) respMap.getOrDefault("status", false);
                    String info = (String) respMap.getOrDefault("info", "");

                    if (!status) {
                        System.out.println("[ERRO] " + info);
                    } else {
                        System.out.println("[SUCESSO] " + info);
                    }

                    // Atualizar token no login
                    if ("usuario_login".equals(respMap.get("operacao"))
                            && Boolean.TRUE.equals(respMap.get("status"))
                            && respMap.containsKey("token")) {
                        token = (String) respMap.get("token");
                        System.out.println("[TOKEN] Logado com sucesso. Token armazenado.");
                    }

                    // Limpar token no logout
                    if ("usuario_logout".equals(respMap.get("operacao"))
                            && Boolean.TRUE.equals(respMap.get("status"))) {
                        token = "";
                        System.out.println("[TOKEN] Token removido.");
                    }

                    // Exibir dados do usuário
                    if ("usuario_ler".equals(respMap.get("operacao")) && respMap.containsKey("usuario")) {
                        Map<String, Object> usuario = (Map<String, Object>) respMap.get("usuario");
                        System.out.println("\n=== DADOS DO USUÁRIO ===");
                        usuario.forEach((k, v) -> System.out.println(k + ": " + v));
                        System.out.println("========================\n");
                    }

                    // Exibir extrato
                    if ("transacao_ler".equals(respMap.get("operacao")) && respMap.containsKey("transacoes")) {
                        List<Map<String, Object>> transacoes = (List<Map<String, Object>>) respMap.get("transacoes");
                        System.out.println("\n=== EXTRATO ===");
                        if (transacoes.isEmpty()) {
                            System.out.println("Nenhuma transação encontrada neste período.");
                        } else {
                            for (Map<String, Object> t : transacoes) {
                                System.out.println("ID: " + t.get("id")
                                        + " | Valor: " + t.get("valor_enviado")
                                        + " | De: " + ((Map) t.get("usuario_enviador")).get("nome")
                                        + " | Para: " + ((Map) t.get("usuario_recebedor")).get("nome")
                                        + " | Data: " + t.get("criado_em"));
                            }
                        }
                        System.out.println("===============\n");
                    }
                } catch (Exception e) {
                    System.out.println("[ERRO] Falha ao processar resposta: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.out.println("[ERRO] Falha ao conectar ao servidor: " + e.getMessage());
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.out.println("[ERRO] Falha ao fechar conexão: " + e.getMessage());
            }
        }
    }

    private static void exibirMenu() {
        System.out.println("\n=== MENU ===");
        System.out.println("1 - Criar usuário");
        System.out.println("2 - Login");
        System.out.println("3 - Ver dados da minha conta");
        System.out.println("4 - Atualizar dados");
        System.out.println("5 - Deletar conta");
        System.out.println("6 - Logout");
        System.out.println("7 - Depositar dinheiro");
        System.out.println("8 - Ver extrato");
        System.out.println("0 - Sair");
        System.out.println("============");
    }
}
