package com.sd;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
                    
                    if ("conectar".equals(operacao)) {
                        resp.put("operacao", "conectar");
                        resp.put("status", true);
                        resp.put("info", "Servidor conectado com sucesso.");
                    } else if ("usuario_criar".equals(operacao)) {
                        String cpf = ((String) req.get("cpf")).trim();
                        try (PreparedStatement st = conn.prepareStatement("SELECT * FROM usuarios WHERE cpf = ?")) {
                            st.setString(1, cpf);
                            ResultSet rs = st.executeQuery();
                            if (!rs.next()) {
                                PreparedStatement ist = conn.prepareStatement("INSERT INTO usuarios (cpf, nome, senha, saldo) VALUES (?, ?, ?, 0.0)");
                                ist.setString(1, cpf);
                                ist.setString(2, ((String) req.get("nome")).trim());
                                ist.setString(3, ((String) req.get("senha")).trim());
                                ist.execute();
                                resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Usuário criado com sucesso.");
                            } else {
                                resp.put("operacao", operacao); resp.put("status", false); resp.put("info", "Ocorreu um erro ao criar usuário.");
                            }
                        }
                    } else if ("usuario_login".equals(operacao)) {
                        String cpf = ((String) req.get("cpf")).trim();
                        String senha = ((String) req.get("senha")).trim();
                        try (PreparedStatement st = conn.prepareStatement("SELECT * FROM usuarios WHERE cpf = ? AND senha = ?")) {
                            st.setString(1, cpf); st.setString(2, senha);
                            ResultSet rs = st.executeQuery();
                            if(rs.next()) {
                                String token = UUID.randomUUID().toString();
                                PreparedStatement stt = conn.prepareStatement("INSERT INTO tokens (token, cpf) VALUES (?, ?)");
                                stt.setString(1, token);
                                stt.setString(2, cpf);
                                stt.execute();
                                resp.put("operacao", operacao);
                                resp.put("token", token);
                                resp.put("status", true);
                                resp.put("info", "Login bem-sucedido.");
                            } else {
                                resp.put("operacao", operacao);
                                resp.put("status", false);
                                resp.put("info", "Ocorreu um erro ao realizar login.");
                            }
                        }
                    } else if ("usuario_ler".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpf = selectCpfByToken(conn, token);
                            if(cpf==null) {resp.put("operacao", operacao);resp.put("status", false);resp.put("info", "Erro ao ler dados do usuário.");}
                            else {
                                try (PreparedStatement st = conn.prepareStatement("SELECT nome, saldo FROM usuarios WHERE cpf = ?")) {
                                    st.setString(1, cpf);
                                    ResultSet rs = st.executeQuery();
                                    if (rs.next()) {
                                        Map<String, Object> usuario = new LinkedHashMap<>();
                                        usuario.put("cpf", cpf);
                                        usuario.put("nome", rs.getString("nome"));
                                        usuario.put("saldo", rs.getDouble("saldo"));
                                        resp.put("operacao", operacao);
                                        resp.put("status", true);
                                        resp.put("info", "Dados do usuário recuperados com sucesso.");
                                        resp.put("usuario", usuario);
                                    } else {
                                        resp.put("operacao", operacao);resp.put("status", false);resp.put("info", "Erro ao ler dados do usuário.");
                                    }
                                }
                            }
                        }
                    } else if ("usuario_atualizar".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpf = selectCpfByToken(conn, token);
                            Map<String, Object> usuarioReq = (Map<String, Object>) req.get("usuario");
                            if (cpf != null && usuarioReq != null && (!usuarioReq.isEmpty())) {
                                if (usuarioReq.containsKey("nome")) {
                                    PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET nome = ? WHERE cpf = ?");
                                    st.setString(1, ((String) usuarioReq.get("nome")).trim());
                                    st.setString(2, cpf); st.execute();
                                }
                                if (usuarioReq.containsKey("senha")) {
                                    PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET senha = ? WHERE cpf = ?");
                                    st.setString(1, ((String) usuarioReq.get("senha")).trim());
                                    st.setString(2, cpf); st.execute();
                                }
                                resp.put("operacao", operacao);resp.put("status", true);resp.put("info", "Usuário atualizado com sucesso.");
                            } else {
                                resp.put("operacao", operacao);resp.put("status", false);resp.put("info", "Erro ao atualizar usuário.");
                            }
                        }
                    } else if ("usuario_deletar".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpf = selectCpfByToken(conn, token);
                            if (cpf != null) {
                                PreparedStatement st = conn.prepareStatement("DELETE FROM usuarios WHERE cpf = ?");
                                st.setString(1, cpf); st.execute();
                                PreparedStatement st2 = conn.prepareStatement("DELETE FROM tokens WHERE cpf = ?");
                                st2.setString(1, cpf); st2.execute();
                                resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Usuário deletado com sucesso.");
                            } else {
                                resp.put("operacao", operacao);resp.put("status", false);resp.put("info", "Erro ao deletar usuário.");
                            }
                        }
                    } else if ("usuario_logout".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            try (PreparedStatement st = conn.prepareStatement("DELETE FROM tokens WHERE token = ?")) {
                                st.setString(1, token);
                                st.execute();
                            }
                            resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Logout realizado com sucesso.");
                        }
                    } else if ("depositar".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpf = selectCpfByToken(conn, token);
                            Double valor = null;
                            if (req.get("valor_enviado") instanceof Number) {
                                valor = ((Number) req.get("valor_enviado")).doubleValue();
                            } else {
                                valor = Double.parseDouble(req.get("valor_enviado").toString());
                            }
                            if (cpf != null && valor != null && valor > 0) {
                                PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET saldo = saldo + ? WHERE cpf = ?");
                                st.setDouble(1, valor); st.setString(2, cpf); st.execute();
                                // Deposito = transação consigo mesmo
                                PreparedStatement tstmt = conn.prepareStatement("INSERT INTO transacoes (valor_enviado, cpf_enviador, cpf_recebedor, criado_em, atualizado_em) VALUES (?, ?, ?, ?, ?)");
                                tstmt.setDouble(1, valor);
                                tstmt.setString(2, cpf);
                                tstmt.setString(3, cpf);
                                String now = isoNow();
                                tstmt.setString(4, now); tstmt.setString(5, now);
                                tstmt.execute();
                                resp.put("operacao", operacao); resp.put("status", true); resp.put("info", "Deposito realizado com sucesso.");
                            } else {
                                resp.put("operacao", operacao); resp.put("status", false); resp.put("info", "Erro ao depositar.");
                            }
                        }
                    } else if ("transacao_ler".equals(operacao)) {
                        String token = (String) req.get("token");
                        if (token == null || token.isBlank()) {
                            resp.put("operacao", operacao);
                            resp.put("status", false);
                            resp.put("info", "Você precisa estar logado para realizar essa ação.");
                        } else {
                            String cpf = selectCpfByToken(conn, token);
                            String dIni = (String) req.get("data_inicial");
                            String dFim = (String) req.get("data_final");
                            List<Map<String, Object>> extrato = new ArrayList<>();
                            String query = "SELECT id, valor_enviado, cpf_enviador, cpf_recebedor, criado_em, atualizado_em FROM transacoes WHERE (cpf_enviador = ? OR cpf_recebedor = ?) AND criado_em >= ? AND criado_em <= ? ORDER BY criado_em ASC";
                            try (PreparedStatement st = conn.prepareStatement(query)) {
                                st.setString(1, cpf);
                                st.setString(2, cpf);
                                st.setString(3, dIni);
                                st.setString(4, dFim);
                                ResultSet rs = st.executeQuery();
                                while (rs.next()) {
                                    Map<String, Object> t = new LinkedHashMap<>();
                                    t.put("id", rs.getInt("id"));
                                    t.put("valor_enviado", rs.getDouble("valor_enviado"));
                                    t.put("usuario_enviador", Map.of("nome", getNomeByCpf(conn, rs.getString("cpf_enviador")), "cpf", rs.getString("cpf_enviador")));
                                    t.put("usuario_recebedor", Map.of("nome", getNomeByCpf(conn, rs.getString("cpf_recebedor")), "cpf", rs.getString("cpf_recebedor")));
                                    t.put("criado_em", rs.getString("criado_em"));
                                    t.put("atualizado_em", rs.getString("atualizado_em"));
                                    extrato.add(t);
                                }
                                resp.put("operacao", operacao);
                                resp.put("status", true);
                                resp.put("info", "Transações recuperadas com sucesso.");
                                resp.put("transacoes", extrato);
                            } catch(Exception ex){
                                resp.put("operacao", operacao); resp.put("status", false); resp.put("info", "Erro ao ler transações.");
                            }
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
