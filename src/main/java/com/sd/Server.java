package com.sd;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class Server extends Thread {
    private final Socket clientSocket;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String DB_URL = "jdbc:sqlite:database.db";

    public Server(Socket clientSocket) {
        this.clientSocket = clientSocket;
        start();
    }

    private static String ts() { return "[" + LocalDateTime.now().format(TS) + "]"; }
    private static String isoNow() { return LocalDateTime.now().format(ISO); }

    // Inicializa o banco com as tabelas
    public static void initDatabase() {
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (cpf TEXT PRIMARY KEY, nome TEXT, senha TEXT, saldo REAL)";
        String sqlTransacoes = "CREATE TABLE IF NOT EXISTS transacoes (id INTEGER PRIMARY KEY AUTOINCREMENT, valor_enviado REAL, cpf_enviador TEXT, cpf_recebedor TEXT, criado_em TEXT, atualizado_em TEXT)";
        String sqlTokens = "CREATE TABLE IF NOT EXISTS tokens (token TEXT PRIMARY KEY, cpf TEXT)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlTransacoes);
            stmt.execute(sqlTokens);
        } catch (SQLException e) {
            System.out.println(ts() + " [DBERR] Erro ao inicializar tabelas: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        String who = clientSocket.getRemoteSocketAddress().toString();
        System.out.println(ts() + " [ACCEPT] Cliente conectado: " + who);
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            Connection conn = DriverManager.getConnection(DB_URL)
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
                    if ("conectar".equals(operacao)) {
                        conectado = true;
                        resp.put("operacao", "conectar");
                        resp.put("status", true);
                        resp.put("info", "Servidor conectado com sucesso.");
                    } ... 
                    // --- continua igual ---
                    else if ("depositar".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpfEnviador = selectCpfByToken(conn, token);
                            String cpfDestino = req.containsKey("cpf_destino") ? ((String) req.get("cpf_destino")).trim() : cpfEnviador; // se não enviar, deposita para si mesmo
                            Double valor = null;
                            if (req.get("valor_enviado") instanceof Number) {
                                valor = ((Number) req.get("valor_enviado")).doubleValue();
                            } else {
                                valor = Double.parseDouble(req.get("valor_enviado").toString());
                            }
                            if (cpfEnviador == null || valor == null || valor <= 0) {
                                resp.put("operacao", operacao);
                                resp.put("status", false);
                                resp.put("info", "Erro ao depositar.");
                            } else {
                                PreparedStatement stDest = conn.prepareStatement("SELECT cpf FROM usuarios WHERE cpf = ?");
                                stDest.setString(1, cpfDestino);
                                ResultSet rsDest = stDest.executeQuery();
                                if (!rsDest.next()) {
                                    resp.put("operacao", operacao);
                                    resp.put("status", false);
                                    resp.put("info", "CPF de destino não encontrado.");
                                } else if (!cpfEnviador.equals(cpfDestino)) {
                                    PreparedStatement stSaldo = conn.prepareStatement("SELECT saldo FROM usuarios WHERE cpf = ?");
                                    stSaldo.setString(1, cpfEnviador);
                                    ResultSet rsSaldo = stSaldo.executeQuery();
                                    if (rsSaldo.next()) {
                                        double saldoAtual = rsSaldo.getDouble("saldo");
                                        if (saldoAtual < valor) {
                                            resp.put("operacao", operacao);
                                            resp.put("status", false);
                                            resp.put("info", "Saldo insuficiente.");
                                        } else {
                                            PreparedStatement stDebito = conn.prepareStatement("UPDATE usuarios SET saldo = saldo - ? WHERE cpf = ?");
                                            stDebito.setDouble(1, valor);
                                            stDebito.setString(2, cpfEnviador);
                                            stDebito.execute();

                                            PreparedStatement stCredito = conn.prepareStatement("UPDATE usuarios SET saldo = saldo + ? WHERE cpf = ?");
                                            stCredito.setDouble(1, valor);
                                            stCredito.setString(2, cpfDestino);
                                            stCredito.execute();

                                            PreparedStatement tstmt = conn.prepareStatement("INSERT INTO transacoes (valor_enviado, cpf_enviador, cpf_recebedor, criado_em, atualizado_em) VALUES (?, ?, ?, ?, ?)");
                                            tstmt.setDouble(1, valor);
                                            tstmt.setString(2, cpfEnviador);
                                            tstmt.setString(3, cpfDestino);
                                            String now = isoNow();
                                            tstmt.setString(4, now); tstmt.setString(5, now);
                                            tstmt.execute();
                                            resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Deposito realizado com sucesso.");
                                        }
                                    } else {
                                        resp.put("operacao", operacao);resp.put("status", false);resp.put("info", "Erro ao consultar saldo.");
                                    }
                                } else {
                                    PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET saldo = saldo + ? WHERE cpf = ?");
                                    st.setDouble(1, valor); st.setString(2, cpfEnviador); st.execute();
                                    PreparedStatement tstmt = conn.prepareStatement("INSERT INTO transacoes (valor_enviado, cpf_enviador, cpf_recebedor, criado_em, atualizado_em) VALUES (?, ?, ?, ?, ?)");
                                    tstmt.setDouble(1, valor);
                                    tstmt.setString(2, cpfEnviador);
                                    tstmt.setString(3, cpfEnviador);
                                    String now = isoNow();
                                    tstmt.setString(4, now); tstmt.setString(5, now);
                                    tstmt.execute();
                                    resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Deposito realizado com sucesso.");
                                }
                            }
                        }
                    }
                    // --- resto igual ---
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
        initDatabase();
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

    // Funções auxiliares para tokens/cpf
    private static String selectCpfByToken(Connection conn, String token) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT cpf FROM tokens WHERE token = ?")) {
            st.setString(1, token);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getString("cpf");
            else return null;
        }
    }
    private static String getNomeByCpf(Connection conn, String cpf) throws SQLException {
        try(PreparedStatement st = conn.prepareStatement("SELECT nome FROM usuarios WHERE cpf = ?")) {
            st.setString(1, cpf);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getString("nome");
            else return "";
        }
    }
}
