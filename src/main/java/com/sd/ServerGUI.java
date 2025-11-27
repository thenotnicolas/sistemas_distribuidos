package com.sd;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class ServerGUI extends JFrame {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String DB_URL = "jdbc:sqlite:database.db";
    
    private JTextField txtPorta;
    private JButton btnIniciar;
    private JButton btnParar;
    private JTextArea txtLog;
    private JTable tableUsuarios;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private JLabel lblConexoes;
    
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;
    private final Map<String, ClientInfo> clientesConectados = Collections.synchronizedMap(new HashMap<>());
    
    private static class ClientInfo {
        String cpf;
        String nome;
        String endereco;
        LocalDateTime loginTime;
        
        ClientInfo(String cpf, String nome, String endereco) {
            this.cpf = cpf;
            this.nome = nome;
            this.endereco = endereco;
            this.loginTime = LocalDateTime.now();
        }
    }

    public ServerGUI() {
        setTitle("Servidor - Sistema Bancário Distribuído");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        initDatabase();
        initComponents();
        
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        JPanel panelControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelControls.setBorder(new TitledBorder("Controle do Servidor"));
        
        panelControls.add(new JLabel("Porta:"));
        txtPorta = new JTextField("8080", 8);
        panelControls.add(txtPorta);
        
        btnIniciar = new JButton("Iniciar Servidor");
        btnIniciar.addActionListener(e -> iniciarServidor());
        panelControls.add(btnIniciar);
        
        btnParar = new JButton("Parar Servidor");
        btnParar.setEnabled(false);
        btnParar.addActionListener(e -> pararServidor());
        panelControls.add(btnParar);
        
        lblStatus = new JLabel("Status: Parado");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        lblStatus.setForeground(Color.RED);
        panelControls.add(lblStatus);
        
        lblConexoes = new JLabel("Conexões: 0");
        panelControls.add(lblConexoes);
        
        add(panelControls, BorderLayout.NORTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(200);
        
        JPanel panelUsuarios = new JPanel(new BorderLayout());
        panelUsuarios.setBorder(new TitledBorder("Usuários Logados (Atualização Automática)"));
        
        String[] colunas = {"CPF", "Nome", "IP", "Login"};
        tableModel = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tableUsuarios = new JTable(tableModel);
        JScrollPane scrollUsuarios = new JScrollPane(tableUsuarios);
        panelUsuarios.add(scrollUsuarios, BorderLayout.CENTER);
        splitPane.setTopComponent(panelUsuarios);
        
        JPanel panelLog = new JPanel(new BorderLayout());
        panelLog.setBorder(new TitledBorder("Log (Mensagens Enviadas/Recebidas)"));
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        panelLog.add(scrollLog, BorderLayout.CENTER);
        
        JPanel panelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnLimpar = new JButton("Limpar Log");
        btnLimpar.addActionListener(e -> txtLog.setText(""));
        panelBotoes.add(btnLimpar);
        panelLog.add(panelBotoes, BorderLayout.SOUTH);
        
        splitPane.setBottomComponent(panelLog);
        add(splitPane, BorderLayout.CENTER);
        
        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> atualizarTabela());
        timer.start();
    }

    private void iniciarServidor() {
        try {
            int porta = Integer.parseInt(txtPorta.getText().trim());
            serverSocket = new ServerSocket(porta);
            isRunning = true;
            btnIniciar.setEnabled(false);
            btnParar.setEnabled(true);
            txtPorta.setEnabled(false);
            lblStatus.setText("Status: Rodando");
            lblStatus.setForeground(Color.GREEN);
            log("[BOOT] Servidor iniciado na porta " + porta);
            
            serverThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket s = serverSocket.accept();
                        new ClientHandler(s).start();
                    } catch (IOException e) {
                        if (isRunning) log("[ERRO] " + e.getMessage());
                    }
                }
            });
            serverThread.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage());
        }
    }

    private void pararServidor() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
        btnIniciar.setEnabled(true);
        btnParar.setEnabled(false);
        txtPorta.setEnabled(true);
        lblStatus.setText("Status: Parado");
        lblStatus.setForeground(Color.RED);
        clientesConectados.clear();
        atualizarTabela();
        log("[STOP] Servidor parado");
    }

    private void atualizarTabela() {
        tableModel.setRowCount(0);
        synchronized (clientesConectados) {
            for (ClientInfo info : clientesConectados.values()) {
                tableModel.addRow(new Object[]{
                    info.cpf, info.nome, info.endereco,
                    info.loginTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                });
            }
        }
        lblConexoes.setText("Conexões: " + clientesConectados.size());
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append("[" + LocalDateTime.now().format(TS) + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (cpf TEXT PRIMARY KEY, nome TEXT, senha TEXT, saldo REAL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS transacoes (id INTEGER PRIMARY KEY AUTOINCREMENT, valor_enviado REAL, cpf_enviador TEXT, cpf_recebedor TEXT, criado_em TEXT, atualizado_em TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS tokens (token TEXT PRIMARY KEY, cpf TEXT)");
        } catch (SQLException e) {}
    }

    private static String isoNow() { return LocalDateTime.now().format(ISO); }
    
    private static String getCpfByToken(Connection conn, String token) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT cpf FROM tokens WHERE token = ?")) {
            st.setString(1, token);
            ResultSet rs = st.executeQuery();
            return rs.next() ? rs.getString("cpf") : null;
        }
    }
    
    private static String getNome(Connection conn, String cpf) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT nome FROM usuarios WHERE cpf = ?")) {
            st.setString(1, cpf);
            ResultSet rs = st.executeQuery();
            return rs.next() ? rs.getString("nome") : "";
        }
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private String addr;
        private String token;

        ClientHandler(Socket s) { 
            this.socket = s; 
            this.addr = s.getRemoteSocketAddress().toString(); 
        }

        @Override
        public void run() {
            log("[ACCEPT] " + addr);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 Connection conn = DriverManager.getConnection(DB_URL)) {
                
                String input;
                while ((input = in.readLine()) != null) {
                    log("[RECV] " + addr + " -> " + input);
                    try {
                        Validator.validateClient(input);
                    } catch (Exception ve) {
                        String err = mapper.writeValueAsString(Map.of("operacao", "erro_protocolo", "status", false, "info", ve.getMessage()));
                        out.println(err);
                        log("[SEND] " + addr + " <- " + err);
                        break;
                    }
                    
                    Map<String, Object> req = mapper.readValue(input, Map.class);
                    String op = (String) req.get("operacao");
                    Map<String, Object> resp = processOp(conn, req, op);
                    String json = mapper.writeValueAsString(resp);
                    out.println(json);
                    log("[SEND] " + addr + " <- " + json);
                }
            } catch (Exception e) {
                log("[EXCP] " + addr + ": " + e.getMessage());
            } finally {
                if (token != null) {
                    clientesConectados.remove(token);
                    atualizarTabela();
                }
                try { socket.close(); } catch (IOException e) {}
                log("[CLOSE] " + addr);
            }
        }

        private Map<String, Object> processOp(Connection conn, Map<String, Object> req, String op) {
            Map<String, Object> resp = new LinkedHashMap<>();
            try {
                if ("conectar".equals(op)) {
                    resp.put("operacao", "conectar");
                    resp.put("status", true);
                    resp.put("info", "Servidor conectado com sucesso.");
                } else if ("usuario_criar".equals(op)) {
                    String cpf = ((String) req.get("cpf")).trim();
                    PreparedStatement st = conn.prepareStatement("SELECT * FROM usuarios WHERE cpf = ?");
                    st.setString(1, cpf);
                    if (!st.executeQuery().next()) {
                        PreparedStatement ist = conn.prepareStatement("INSERT INTO usuarios VALUES (?, ?, ?, 0.0)");
                        ist.setString(1, cpf);
                        ist.setString(2, ((String) req.get("nome")).trim());
                        ist.setString(3, ((String) req.get("senha")).trim());
                        ist.execute();
                        resp.put("operacao", op); resp.put("status", true); resp.put("info", "Usuário criado com sucesso.");
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Usuário já cadastrado.");
                    }
                } else if ("usuario_login".equals(op)) {
                    String cpf = ((String) req.get("cpf")).trim();
                    String senha = ((String) req.get("senha")).trim());
                    PreparedStatement st = conn.prepareStatement("SELECT * FROM usuarios WHERE cpf = ? AND senha = ?");
                    st.setString(1, cpf); st.setString(2, senha);
                    ResultSet rs = st.executeQuery();
                    if (rs.next()) {
                        token = UUID.randomUUID().toString();
                        PreparedStatement ist = conn.prepareStatement("INSERT INTO tokens VALUES (?, ?)");
                        ist.setString(1, token); ist.setString(2, cpf);
                        ist.execute();
                        clientesConectados.put(token, new ClientInfo(cpf, rs.getString("nome"), addr));
                        atualizarTabela();
                        resp.put("operacao", op); resp.put("token", token); resp.put("status", true); resp.put("info", "Login bem-sucedido.");
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Ocorreu um erro ao realizar login.");
                    }
                } else if ("usuario_ler".equals(op)) {
                    String tk = (String) req.get("token");
                    if (tk == null || tk.isBlank()) {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Você precisa estar logado para realizar essa ação.");
                    } else {
                        String cpf = getCpfByToken(conn, tk);
                        if (cpf == null) {
                            resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro ao ler dados do usuário.");
                        } else {
                            PreparedStatement st = conn.prepareStatement("SELECT nome, saldo FROM usuarios WHERE cpf = ?");
                            st.setString(1, cpf);
                            ResultSet rs = st.executeQuery();
                            if (rs.next()) {
                                Map<String, Object> usr = new LinkedHashMap<>();
                                usr.put("cpf", cpf); usr.put("nome", rs.getString("nome")); usr.put("saldo", rs.getDouble("saldo"));
                                resp.put("operacao", op); resp.put("status", true); resp.put("info", "Dados do usuário recuperados com sucesso."); resp.put("usuario", usr);
                            }
                        }
                    }
                } else if ("usuario_atualizar".equals(op)) {
                    String tk = (String) req.get("token");
                    String cpf = getCpfByToken(conn, tk);
                    Map<String, Object> usr = (Map) req.get("usuario");
                    if (cpf != null && usr != null && !usr.isEmpty()) {
                        if (usr.containsKey("nome")) {
                            PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET nome = ? WHERE cpf = ?");
                            st.setString(1, ((String) usr.get("nome")).trim()); st.setString(2, cpf); st.execute();
                            if (clientesConectados.containsKey(tk)) {
                                clientesConectados.get(tk).nome = ((String) usr.get("nome")).trim();
                                atualizarTabela();
                            }
                        }
                        if (usr.containsKey("senha")) {
                            PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET senha = ? WHERE cpf = ?");
                            st.setString(1, ((String) usr.get("senha")).trim()); st.setString(2, cpf); st.execute();
                        }
                        resp.put("operacao", op); resp.put("status", true); resp.put("info", "Usuário atualizado com sucesso.");
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro ao atualizar usuário.");
                    }
                } else if ("usuario_deletar".equals(op)) {
                    String tk = (String) req.get("token");
                    String cpf = getCpfByToken(conn, tk);
                    if (cpf != null) {
                        PreparedStatement st = conn.prepareStatement("DELETE FROM usuarios WHERE cpf = ?");
                        st.setString(1, cpf); st.execute();
                        PreparedStatement st2 = conn.prepareStatement("DELETE FROM tokens WHERE cpf = ?");
                        st2.setString(1, cpf); st2.execute();
                        clientesConectados.remove(tk);
                        atualizarTabela();
                        resp.put("operacao", op); resp.put("status", true); resp.put("info", "Usuário deletado com sucesso.");
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro ao deletar usuário.");
                    }
                } else if ("usuario_logout".equals(op)) {
                    String tk = (String) req.get("token");
                    PreparedStatement st = conn.prepareStatement("DELETE FROM tokens WHERE token = ?");
                    st.setString(1, tk); st.execute();
                    clientesConectados.remove(tk);
                    atualizarTabela();
                    resp.put("operacao", op); resp.put("status", true); resp.put("info", "Logout realizado com sucesso.");
                } else if ("depositar".equals(op)) {
                    String tk = (String) req.get("token");
                    String cpf = getCpfByToken(conn, tk);
                    Double val = ((Number) req.get("valor_enviado")).doubleValue();
                    if (cpf != null && val > 0) {
                        PreparedStatement st = conn.prepareStatement("UPDATE usuarios SET saldo = saldo + ? WHERE cpf = ?");
                        st.setDouble(1, val); st.setString(2, cpf); st.execute();
                        PreparedStatement ist = conn.prepareStatement("INSERT INTO transacoes VALUES (NULL, ?, ?, ?, ?, ?)");
                        ist.setDouble(1, val); ist.setString(2, cpf); ist.setString(3, cpf);
                        String now = isoNow(); ist.setString(4, now); ist.setString(5, now); ist.execute();
                        resp.put("operacao", op); resp.put("status", true); resp.put("info", "Depósito realizado com sucesso.");
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro ao depositar.");
                    }
                } else if ("transacao_criar".equals(op)) {
                    String tk = (String) req.get("token");
                    String cpfDest = (String) req.get("cpf_destino");
                    Double val = ((Number) req.get("valor")).doubleValue();
                    String cpfOrig = getCpfByToken(conn, tk);
                    if (cpfOrig != null && val > 0) {
                        PreparedStatement stDest = conn.prepareStatement("SELECT saldo FROM usuarios WHERE cpf = ?");
                        stDest.setString(1, cpfDest);
                        if (!stDest.executeQuery().next()) {
                            resp.put("operacao", op); resp.put("status", false); resp.put("info", "CPF destino não existe.");
                        } else {
                            PreparedStatement stOrig = conn.prepareStatement("SELECT saldo FROM usuarios WHERE cpf = ?");
                            stOrig.setString(1, cpfOrig);
                            ResultSet rs = stOrig.executeQuery();
                            if (!rs.next() || rs.getDouble("saldo") < val) {
                                resp.put("operacao", op); resp.put("status", false); resp.put("info", "Saldo insuficiente para transferência.");
                            } else {
                                conn.setAutoCommit(false);
                                try {
                                    PreparedStatement stSaida = conn.prepareStatement("UPDATE usuarios SET saldo = saldo - ? WHERE cpf = ?");
                                    stSaida.setDouble(1, val); stSaida.setString(2, cpfOrig); stSaida.execute();
                                    PreparedStatement stEntrada = conn.prepareStatement("UPDATE usuarios SET saldo = saldo + ? WHERE cpf = ?");
                                    stEntrada.setDouble(1, val); stEntrada.setString(2, cpfDest); stEntrada.execute();
                                    PreparedStatement ist = conn.prepareStatement("INSERT INTO transacoes VALUES (NULL, ?, ?, ?, ?, ?)");
                                    String now = isoNow(); ist.setDouble(1, val); ist.setString(2, cpfOrig); ist.setString(3, cpfDest);
                                    ist.setString(4, now); ist.setString(5, now); ist.execute();
                                    conn.commit();
                                    resp.put("operacao", op); resp.put("status", true); resp.put("info", "Transferência PIX realizada com sucesso.");
                                } catch (Exception e) {
                                    conn.rollback();
                                    resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro ao transferir: " + e.getMessage());
                                } finally {
                                    conn.setAutoCommit(true);
                                }
                            }
                        }
                    } else {
                        resp.put("operacao", op); resp.put("status", false); resp.put("info", "Dados inválidos para transferência.");
                    }
                } else if ("transacao_ler".equals(op)) {
                    String tk = (String) req.get("token");
                    String cpf = getCpfByToken(conn, tk);
                    String dIni = (String) req.get("data_inicial");
                    String dFim = (String) req.get("data_final");
                    List<Map<String, Object>> ext = new ArrayList<>();
                    PreparedStatement st = conn.prepareStatement("SELECT * FROM transacoes WHERE (cpf_enviador = ? OR cpf_recebedor = ?) AND criado_em >= ? AND criado_em <= ? ORDER BY criado_em");
                    st.setString(1, cpf); st.setString(2, cpf); st.setString(3, dIni); st.setString(4, dFim);
                    ResultSet rs = st.executeQuery();
                    while (rs.next()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("id", rs.getInt("id")); t.put("valor_enviado", rs.getDouble("valor_enviado"));
                        t.put("usuario_enviador", Map.of("nome", getNome(conn, rs.getString("cpf_enviador")), "cpf", rs.getString("cpf_enviador")));
                        t.put("usuario_recebedor", Map.of("nome", getNome(conn, rs.getString("cpf_recebedor")), "cpf", rs.getString("cpf_recebedor")));
                        t.put("criado_em", rs.getString("criado_em")); t.put("atualizado_em", rs.getString("atualizado_em"));
                        ext.add(t);
                    }
                    resp.put("operacao", op); resp.put("status", true); resp.put("info", "Transações recuperadas com sucesso."); resp.put("transacoes", ext);
                } else {
                    resp.put("operacao", op); resp.put("status", false); resp.put("info", "Operação desconhecida.");
                }
            } catch (Exception e) {
                resp.put("operacao", op); resp.put("status", false); resp.put("info", "Erro interno: " + e.getMessage());
            }
            return resp;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerGUI());
    }
}
