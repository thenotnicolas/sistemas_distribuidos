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
        System.out.print("Qual o IP do servidor? ");
        String serverIP = br.readLine();
        System.out.print("Qual a Porta do servidor? ");
        int serverPort = Integer.parseInt(br.readLine());

        try (Socket socket = new Socket(serverIP, serverPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Conectar primeiro
            Map<String, Object> msgConectar = Map.of("operacao", "conectar");
            String jsonConectar = mapper.writeValueAsString(msgConectar);
            System.out.println("DEBUG JSON conectar enviado: '" + jsonConectar + "'");
            try {
                Validator.validateClient(jsonConectar);
            } catch (Exception e) {
                System.out.println("Falha ao validar 'conectar': " + e.getMessage());
                return;
            }
            out.println(jsonConectar);

            String resposta = in.readLine();
            if (resposta == null || "null".equals(resposta)) {
                System.out.println("Servidor encerrou a conexão durante o handshake.");
                return;
            }
            try {
                Validator.validateServer(resposta);
            } catch (Exception e) {
                System.out.println("Resposta inválida do servidor no handshake: " + e.getMessage());
                return;
            }
            System.out.println("Servidor: " + resposta);

            // Loop principal
            while (true) {
                System.out.println("\nMenu:");
                System.out.println("1 - Criar usuário");
                System.out.println("2 - Login");
                System.out.println("3 - Ler dados");
                System.out.println("4 - Atualizar dados");
                System.out.println("5 - Deletar conta");
                System.out.println("6 - Logout");
                System.out.println("7 - Depositar dinheiro");
                System.out.println("8 - Solicitar extrato");
                System.out.println("9 - Transferir para outra conta");
                System.out.println("0 - Sair");
                System.out.print("Escolha: ");
                String op = br.readLine();

                // Verificação de login para ações que exigem token
                if (("3 4 5 6 7 8 9").contains(op)) {
                    if (token == null || token.isBlank()) {
                        System.out.println("Você precisa estar logado para realizar essa ação.");
                        continue;
                    }
                }

                Map<String, Object> mensagem = new LinkedHashMap<>();
                if ("1".equals(op)) {
                    mensagem.put("operacao", "usuario_criar");
                    System.out.print("Nome (mín 6 caract.): ");
                    mensagem.put("nome", br.readLine());
                    System.out.print("CPF (formato 000.000.000-00): ");
                    mensagem.put("cpf", br.readLine());
                    System.out.print("Senha (mín 6 caract.): ");
                    mensagem.put("senha", br.readLine());
                } else if ("2".equals(op)) {
                    mensagem.put("operacao", "usuario_login");
                    System.out.print("CPF (formato 000.000.000-00): ");
                    mensagem.put("cpf", br.readLine());
                    System.out.print("Senha (mín 6 caract.): ");
                    mensagem.put("senha", br.readLine());
                } else if ("3".equals(op)) {
                    mensagem.put("operacao", "usuario_ler");
                    mensagem.put("token", token);
                } else if ("4".equals(op)) {
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
                    mensagem.put("operacao", "usuario_deletar");
                    mensagem.put("token", token);
                } else if ("6".equals(op)) {
                    mensagem.put("operacao", "usuario_logout");
                    mensagem.put("token", token);
                } else if ("7".equals(op)) {
                    mensagem.put("operacao", "depositar");
                    mensagem.put("token", token);
                    System.out.print("Valor a depositar: ");
                    String valorStr = br.readLine().replace(",",".");
                    double val;
                    try {
                        val = Double.parseDouble(valorStr);
                    } catch (NumberFormatException e) {
                        System.out.println("Formato inválido! Use ponto como separador decimal, ex: 52.30");
                        continue;
                    }
                    mensagem.put("valor_enviado", val);
                } else if ("8".equals(op)) {
                    mensagem.put("operacao", "transacao_ler");
                    mensagem.put("token", token);
                    System.out.print("Data inicial (ex: 2025-11-01T00:00:00Z): ");
                    String dIni = br.readLine();
                    System.out.print("Data final (ex: 2025-11-17T23:59:59Z): ");
                    String dFim = br.readLine();
                    mensagem.put("data_inicial", dIni);
                    mensagem.put("data_final", dFim);
                } else if ("9".equals(op)) {
                    mensagem.put("operacao", "transacao_criar");
                    mensagem.put("token", token);
                    System.out.print("CPF de destino (formato 000.000.000-00): ");
                    mensagem.put("cpf_destino", br.readLine());
                    System.out.print("Valor a transferir: ");
                    String valorStr = br.readLine().replace(",",".");
                    double val;
                    try {
                        val = Double.parseDouble(valorStr);
                    } catch (NumberFormatException e) {
                        System.out.println("Formato inválido! Use ponto como separador decimal, ex: 52.30");
                        continue;
                    }
                    mensagem.put("valor", val);
                } else if ("0".equals(op)) {
                    System.out.println("Cliente finalizado.");
                    break;
                } else {
                    System.out.println("Opção inválida.");
                    continue;
                }

                String jsonEnvio = mapper.writeValueAsString(mensagem);
                try {
                    Validator.validateClient(jsonEnvio);
                } catch (Exception e) {
                    System.out.println("Entrada inválida: " + e.getMessage());
                    continue;
                }
                out.println(jsonEnvio);
                String respostaServer;
                try {
                    respostaServer = in.readLine();
                } catch (java.net.SocketException se) {
                    System.out.println("Conexão encerrada pelo servidor: " + se.getMessage());
                    break;
                }
                if (respostaServer == null || "null".equals(respostaServer)) {
                    System.out.println("Servidor encerrou a conexão (mensagem inválida/protocolo). Reinicie o client.");
                    break;
                }
                try {
                    Validator.validateServer(respostaServer);
                } catch (Exception e) {
                    System.out.println("Resposta inválida do servidor: " + e.getMessage());
                    break;
                }
                System.out.println("Resposta: " + respostaServer);
                try {
                    Map<String, Object> respMap = mapper.readValue(respostaServer, Map.class);
                    if ("usuario_login".equals(respMap.get("operacao"))
                            && Boolean.TRUE.equals(respMap.get("status"))
                            && respMap.containsKey("token")) {
                        token = (String) respMap.get("token");
                    }
                    if ("usuario_logout".equals(respMap.get("operacao"))
                            && Boolean.TRUE.equals(respMap.get("status"))) {
                        token = "";
                    }
                    if ("transacao_ler".equals(respMap.get("operacao")) && respMap.containsKey("transacoes")) {
                        List<Map<String, Object>> transacoes = (List<Map<String, Object>>) respMap.get("transacoes");
                        System.out.println("Extrato:");
                        for (Map<String, Object> t : transacoes) {
                            System.out.println("ID: " + t.get("id") +
                                               " | Valor: " + t.get("valor_enviado") +
                                               " | De: " + ((Map)t.get("usuario_enviador")).get("nome") +
                                               " | Para: " + ((Map)t.get("usuario_recebedor")).get("nome") +
                                               " | Data: " + t.get("criado_em"));
                        }
                    }
                } catch (Exception ignore) {}
            }
        }
    }
}