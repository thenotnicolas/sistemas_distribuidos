package com.sd;

import java.io.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Client {
    private static String token = "";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String serverIP = "";
        int serverPort = 0;
        boolean connected = false;

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
            connected = true;
            System.out.println("[INFO] Conectado ao servidor em " + serverIP + ":" + serverPort);
        } catch (IOException e) {
            System.out.println("[ERRO] Falha ao conectar ao servidor: " + e.getMessage());
            System.exit(1);
        }

        while (connected) {
            try {
                exibirMenu();
                System.out.print("Escolha uma opção: ");
                String opcao = br.readLine();

                Map<String, Object> mensagem = new HashMap<>();

                switch (opcao) {
                    case "1":
                        // Cadastrar usuário
                        System.out.print("Nome: ");
                        String nome = br.readLine();
                        System.out.print("CPF: ");
                        String cpf = br.readLine();
                        System.out.print("Senha: ");
                        String senha = br.readLine();
                        System.out.print("Email: ");
                        String email = br.readLine();
                        System.out.print("Telefone: ");
                        String telefone = br.readLine();

                        mensagem.put("operacao", "usuario_criar");
                        mensagem.put("nome", nome);
                        mensagem.put("cpf", cpf);
                        mensagem.put("senha", senha);
                        mensagem.put("email", email);
                        mensagem.put("telefone", telefone);
                        break;

                    case "2":
                        // Login
                        System.out.print("CPF: ");
                        cpf = br.readLine();
                        System.out.print("Senha: ");
                        senha = br.readLine();

                        mensagem.put("operacao", "usuario_login");
                        mensagem.put("cpf", cpf);
                        mensagem.put("senha", senha);
                        break;

                    case "3":
                        // Logout
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        mensagem.put("operacao", "usuario_logout");
                        mensagem.put("token", token);
                        token = "";
                        break;

                    case "4":
                        // Ler dados do usuário
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        mensagem.put("operacao", "usuario_ler");
                        mensagem.put("token", token);
                        break;

                    case "5":
                        // Atualizar dados do usuário
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        System.out.print("Novo nome (ou deixe em branco para manter): ");
                        nome = br.readLine();
                        System.out.print("Novo email (ou deixe em branco para manter): ");
                        email = br.readLine();
                        System.out.print("Novo telefone (ou deixe em branco para manter): ");
                        telefone = br.readLine();

                        mensagem.put("operacao", "usuario_atualizar");
                        mensagem.put("token", token);
                        if (!nome.isEmpty()) mensagem.put("nome", nome);
                        if (!email.isEmpty()) mensagem.put("email", email);
                        if (!telefone.isEmpty()) mensagem.put("telefone", telefone);
                        break;

                    case "6":
                        // Deletar usuário
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        System.out.print("Deseja realmente deletar sua conta? (s/n): ");
                        String confirmacao = br.readLine();
                        if (!confirmacao.equalsIgnoreCase("s")) {
                            System.out.println("[INFO] Operação cancelada.");
                            continue;
                        }
                        mensagem.put("operacao", "usuario_deletar");
                        mensagem.put("token", token);
                        break;

                    case "7":
                        // Depositar dinheiro
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        System.out.print("Valor a depositar (use ponto como separador, ex: 99.50): ");
                        String valorStr = br.readLine().trim();
                        double valor = 0;
                        try {
                            valor = Double.parseDouble(valorStr);
                            if (valor <= 0) {
                                System.out.println("[ERRO] O valor deve ser maior que zero.");
                                continue;
                            }
                        } catch (NumberFormatException nfe) {
                            System.out.println("[ERRO] Formato inválido. Digite o valor usando ponto como separador (ex: 99.50).");
                            continue;
                        }
                        mensagem.put("operacao", "depositar");
                        mensagem.put("token", token);
                        mensagem.put("valor_enviado", valor);
                        break;

                    case "8":
                        // Solicitar extrato
                        if (token.isEmpty()) {
                            System.out.println("[AVISO] Você precisa fazer login antes de realizar essa operação.");
                            continue;
                        }
                        System.out.print("Data inicial (ISO 8601, ex: 2025-01-01T00:00:00Z): ");
                        String dataInicial = br.readLine();
                        System.out.print("Data final (ISO 8601, ex: 2025-12-31T23:59:59Z): ");
                        String dataFinal = br.readLine();

                        mensagem.put("operacao", "transacao_ler");
                        mensagem.put("token", token);
                        mensagem.put("data_inicial", dataInicial);
                        mensagem.put("data_final", dataFinal);
                        break;

                    case "9":
                        // Sair
                        System.out.println("[INFO] Desconectando...");
                        connected = false;
                        continue;

                    default:
                        System.out.println("[AVISO] Opção inválida!");
                        continue;
                }

                // Validar e enviar mensagem
                if (Validator.validar(mapper.writeValueAsString(mensagem))) {
                    String jsonMensagem = mapper.writeValueAsString(mensagem);
                    System.out.println("[ENVIADO] " + jsonMensagem);
                    out.println(jsonMensagem);

                    String respostaServer = in.readLine();
                    if (respostaServer != null) {
                        System.out.println("[RECEBIDO] " + respostaServer);

                        try {
                            Map<String, Object> resposta = mapper.readValue(respostaServer, Map.class);
                            boolean status = (Boolean) resposta.getOrDefault("status", false);
                            String info = (String) resposta.getOrDefault("info", "");

                            if (!status) {
                                System.out.println("[ERRO] " + info);
                            } else {
                                System.out.println("[SUCESSO] " + info);
                                
                                // Atualizar token se login bem-sucedido
                                if ("usuario_login".equals(mensagem.get("operacao")) && resposta.containsKey("token")) {
                                    token = (String) resposta.get("token");
                                    System.out.println("[TOKEN] Logado com sucesso. Token armazenado.");
                                }

                                // Exibir dados se ler usuário
                                if ("usuario_ler".equals(mensagem.get("operacao")) && resposta.containsKey("usuario")) {
                                    Map<String, Object> usuario = (Map<String, Object>) resposta.get("usuario");
                                    System.out.println("\n=== DADOS DO USUÁRIO ===");
                                    usuario.forEach((k, v) -> System.out.println(k + ": " + v));
                                    System.out.println("========================\n");
                                }

                                // Exibir extrato se transacao_ler bem-sucedido
                                if ("transacao_ler".equals(mensagem.get("operacao")) && resposta.containsKey("transacoes")) {
                                    List<Map<String, Object>> transacoes = (List<Map<String, Object>>) resposta.get("transacoes");
                                    System.out.println("\n=== EXTRATO ===");
                                    if (transacoes.isEmpty()) {
                                        System.out.println("Nenhuma transação encontrada neste período.");
                                    } else {
                                        for (Map<String, Object> t : transacoes) {
                                            System.out.println("ID: " + t.get("id"));
                                            System.out.println("Valor: " + t.get("valor_enviado"));
                                            System.out.println("De: " + t.get("usuario_enviador"));
                                            System.out.println("Para: " + t.get("usuario_recebedor"));
                                            System.out.println("Data: " + t.get("criado_em"));
                                            System.out.println("---");
                                        }
                                    }
                                    System.out.println("===============\n");
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("[ERRO] Falha ao processar resposta do servidor: " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("[ERRO] Mensagem inválida. Não atende o protocolo.");
                }

            } catch (IOException e) {
                System.out.println("[ERRO] Conexão perdida: " + e.getMessage());
                connected = false;
            }
        }

        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("[ERRO] Falha ao fechar conexão: " + e.getMessage());
        }
    }

    private static void exibirMenu() {
        System.out.println("\n=== MENU ===");
        System.out.println("1 - Cadastrar");
        System.out.println("2 - Login");
        System.out.println("3 - Logout");
        System.out.println("4 - Ver dados da minha conta");
        System.out.println("5 - Atualizar dados da minha conta");
        System.out.println("6 - Deletar minha conta");
        System.out.println("7 - Depositar dinheiro");
        System.out.println("8 - Ver extrato");
        System.out.println("9 - Sair");
        System.out.println("============");
    }
}
