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
                System.out.println("0 - Sair");
                System.out.print("Escolha: ");
                String op = br.readLine();

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
                } else if ("0".equals(op)) {
                    System.out.println("Cliente finalizado.");
                    break;
                } else {
                    System.out.println("Opção inválida.");
                    continue;
                }

                // NÃO ENVIAR se a validação falhar
                String jsonEnvio = mapper.writeValueAsString(mensagem);
                try {
                    Validator.validateClient(jsonEnvio);
                } catch (Exception e) {
                    System.out.println("Entrada inválida: " + e.getMessage());
                    // Volta ao menu sem enviar nada
                    continue;
                }

                // Envia somente se válido
                out.println(jsonEnvio);

                // Ler resposta (tratar null/"null" e conexão reset)
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

                // Atualizar token conforme respostas
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
                } catch (Exception ignore) {
                }
            }
        }
    }
}
