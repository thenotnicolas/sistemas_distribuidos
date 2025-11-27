package com.sd;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import validador.Validator;

public class ClientGUI extends JFrame {
    private static final ObjectMapper mapper = new ObjectMapper();
    private String token = "";
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    // Componentes de conexão
    private JTextField txtServerIP;
    private JTextField txtServerPort;
    private JButton btnConnect;
    
    // Área de mensagens
    private JTextArea txtMessages;
    private JScrollPane scrollMessages;
    
    // Painéis de operações
    private JTabbedPane tabbedPane;
    private JPanel panelAuth;
    private JPanel panelAccount;
    private JPanel panelTransactions;
    
    // Componentes de autenticação
    private JTextField txtCPFCadastro;
    private JTextField txtNomeCadastro;
    private JPasswordField txtSenhaCadastro;
    private JButton btnCriarUsuario;
    
    private JTextField txtCPFLogin;
    private JPasswordField txtSenhaLogin;
    private JButton btnLogin;
    private JButton btnLogout;
    
    // Componentes de conta
    private JLabel lblSaldoAtual;
    private JButton btnConsultarDados;
    private JButton btnAtualizarSaldo;
    private JTextField txtNovoNome;
    private JPasswordField txtNovaSenha;
    private JButton btnAtualizarDados;
    private JButton btnDeletarConta;
    
    // Componentes de transações
    private JTextField txtValorDeposito;
    private JButton btnDepositar;
    
    private JTextField txtCPFDestino;
    private JTextField txtValorPix;
    private JButton btnEnviarPix;
    
    private JTextField txtDataInicial;
    private JTextField txtDataFinal;
    private JButton btnConsultarExtrato;
    
    private boolean isConnected = false;

    public ClientGUI() {
        setTitle("Cliente - Sistema Bancário Distribuído");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        initComponents();
        
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        // Painel superior - Conexão
        JPanel panelConnection = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelConnection.setBorder(new TitledBorder("Conexão com Servidor"));
        
        panelConnection.add(new JLabel("IP do Servidor:"));
        txtServerIP = new JTextField("localhost", 15);
        panelConnection.add(txtServerIP);
        
        panelConnection.add(new JLabel("Porta:"));
        txtServerPort = new JTextField("8080", 8);
        panelConnection.add(txtServerPort);
        
        btnConnect = new JButton("Conectar");
        btnConnect.addActionListener(e -> conectarServidor());
        panelConnection.add(btnConnect);
        
        add(panelConnection, BorderLayout.NORTH);
        
        // Painel central - Abas de operações
        tabbedPane = new JTabbedPane();
        
        // Aba de Autenticação
        panelAuth = createAuthPanel();
        tabbedPane.addTab("Autenticação", panelAuth);
        
        // Aba de Conta
        panelAccount = createAccountPanel();
        tabbedPane.addTab("Minha Conta", panelAccount);
        tabbedPane.setEnabledAt(1, false);
        
        // Aba de Transações
        panelTransactions = createTransactionsPanel();
        tabbedPane.addTab("Transações", panelTransactions);
        tabbedPane.setEnabledAt(2, false);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Painel inferior - Mensagens
        JPanel panelMessages = new JPanel(new BorderLayout());
        panelMessages.setBorder(new TitledBorder("Mensagens Enviadas/Recebidas"));
        
        txtMessages = new JTextArea(10, 70);
        txtMessages.setEditable(false);
        txtMessages.setFont(new Font("Monospaced", Font.PLAIN, 11));
        scrollMessages = new JScrollPane(txtMessages);
        panelMessages.add(scrollMessages, BorderLayout.CENTER);
        
        add(panelMessages, BorderLayout.SOUTH);
    }

    private JPanel createAuthPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Criar Usuário
        JPanel panelCriar = new JPanel(new GridBagLayout());
        panelCriar.setBorder(new TitledBorder("Criar Nova Conta"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panelCriar.add(new JLabel("CPF (000.000.000-00):"), gbc);
        gbc.gridx = 1;
        txtCPFCadastro = new JTextField(20);
        panelCriar.add(txtCPFCadastro, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panelCriar.add(new JLabel("Nome (mín. 6 caracteres):"), gbc);
        gbc.gridx = 1;
        txtNomeCadastro = new JTextField(20);
        panelCriar.add(txtNomeCadastro, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        panelCriar.add(new JLabel("Senha (mín. 6 caracteres):"), gbc);
        gbc.gridx = 1;
        txtSenhaCadastro = new JPasswordField(20);
        panelCriar.add(txtSenhaCadastro, gbc);
        
        gbc.gridx = 1; gbc.gridy = 3;
        btnCriarUsuario = new JButton("Criar Usuário");
        btnCriarUsuario.addActionListener(e -> criarUsuario());
        panelCriar.add(btnCriarUsuario, gbc);
        
        panel.add(panelCriar);
        panel.add(Box.createVerticalStrut(20));
        
        // Login
        JPanel panelLogin = new JPanel(new GridBagLayout());
        panelLogin.setBorder(new TitledBorder("Login"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panelLogin.add(new JLabel("CPF:"), gbc);
        gbc.gridx = 1;
        txtCPFLogin = new JTextField(20);
        panelLogin.add(txtCPFLogin, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panelLogin.add(new JLabel("Senha:"), gbc);
        gbc.gridx = 1;
        txtSenhaLogin = new JPasswordField(20);
        panelLogin.add(txtSenhaLogin, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        JPanel panelLoginButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnLogin = new JButton("Entrar");
        btnLogin.addActionListener(e -> fazerLogin());
        panelLoginButtons.add(btnLogin);
        
        btnLogout = new JButton("Sair");
        btnLogout.addActionListener(e -> fazerLogout());
        btnLogout.setEnabled(false);
        panelLoginButtons.add(btnLogout);
        
        panelLogin.add(panelLoginButtons, gbc);
        
        panel.add(panelLogin);
        
        return panel;
    }

    private JPanel createAccountPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Saldo
        JPanel panelSaldo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelSaldo.setBorder(new TitledBorder("Saldo"));
        lblSaldoAtual = new JLabel("R$ 0,00");
        lblSaldoAtual.setFont(new Font("Arial", Font.BOLD, 18));
        panelSaldo.add(lblSaldoAtual);
        
        btnConsultarDados = new JButton("Consultar Dados");
        btnConsultarDados.addActionListener(e -> consultarDados());
        panelSaldo.add(btnConsultarDados);
        
        btnAtualizarSaldo = new JButton("Atualizar Saldo");
        btnAtualizarSaldo.addActionListener(e -> atualizarSaldo());
        panelSaldo.add(btnAtualizarSaldo);
        
        panel.add(panelSaldo);
        panel.add(Box.createVerticalStrut(20));
        
        // Atualizar Dados
        JPanel panelAtualizar = new JPanel(new GridBagLayout());
        panelAtualizar.setBorder(new TitledBorder("Atualizar Dados da Conta"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panelAtualizar.add(new JLabel("Novo Nome (deixe vazio para manter):"), gbc);
        gbc.gridx = 1;
        txtNovoNome = new JTextField(20);
        panelAtualizar.add(txtNovoNome, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panelAtualizar.add(new JLabel("Nova Senha (deixe vazio para manter):"), gbc);
        gbc.gridx = 1;
        txtNovaSenha = new JPasswordField(20);
        panelAtualizar.add(txtNovaSenha, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        btnAtualizarDados = new JButton("Atualizar Dados");
        btnAtualizarDados.addActionListener(e -> atualizarDados());
        panelAtualizar.add(btnAtualizarDados, gbc);
        
        panel.add(panelAtualizar);
        panel.add(Box.createVerticalStrut(20));
        
        // Deletar Conta
        JPanel panelDeletar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelDeletar.setBorder(new TitledBorder("Zona de Perigo"));
        btnDeletarConta = new JButton("Deletar Conta");
        btnDeletarConta.setForeground(Color.RED);
        btnDeletarConta.addActionListener(e -> deletarConta());
        panelDeletar.add(btnDeletarConta);
        
        panel.add(panelDeletar);
        
        return panel;
    }

    private JPanel createTransactionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Depósito
        JPanel panelDeposito = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelDeposito.setBorder(new TitledBorder("Depósito"));
        panelDeposito.add(new JLabel("Valor (ex: 100.50):"));
        txtValorDeposito = new JTextField(15);
        panelDeposito.add(txtValorDeposito);
        btnDepositar = new JButton("Depositar");
        btnDepositar.addActionListener(e -> depositar());
        panelDeposito.add(btnDepositar);
        
        panel.add(panelDeposito);
        panel.add(Box.createVerticalStrut(15));
        
        // PIX
        JPanel panelPix = new JPanel(new GridBagLayout());
        panelPix.setBorder(new TitledBorder("Transferência PIX"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panelPix.add(new JLabel("CPF Destino:"), gbc);
        gbc.gridx = 1;
        txtCPFDestino = new JTextField(20);
        panelPix.add(txtCPFDestino, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panelPix.add(new JLabel("Valor:"), gbc);
        gbc.gridx = 1;
        txtValorPix = new JTextField(20);
        panelPix.add(txtValorPix, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        btnEnviarPix = new JButton("Enviar PIX");
        btnEnviarPix.addActionListener(e -> enviarPix());
        panelPix.add(btnEnviarPix, gbc);
        
        panel.add(panelPix);
        panel.add(Box.createVerticalStrut(15));
        
        // Extrato
        JPanel panelExtrato = new JPanel(new GridBagLayout());
        panelExtrato.setBorder(new TitledBorder("Consultar Extrato"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panelExtrato.add(new JLabel("Data Inicial (yyyy-MM-ddTHH:mm:ssZ):"), gbc);
        gbc.gridx = 1;
        txtDataInicial = new JTextField("2025-01-01T00:00:00Z", 20);
        panelExtrato.add(txtDataInicial, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panelExtrato.add(new JLabel("Data Final (yyyy-MM-ddTHH:mm:ssZ):"), gbc);
        gbc.gridx = 1;
        txtDataFinal = new JTextField("2025-12-31T23:59:59Z", 20);
        panelExtrato.add(txtDataFinal, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        btnConsultarExtrato = new JButton("Consultar Extrato");
        btnConsultarExtrato.addActionListener(e -> consultarExtrato());
        panelExtrato.add(btnConsultarExtrato, gbc);
        
        panel.add(panelExtrato);
        
        return panel;
    }

    private void conectarServidor() {
        if (isConnected) {
            showMessage("Já está conectado ao servidor!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String serverIP = txtServerIP.getText().trim();
        String portStr = txtServerPort.getText().trim();
        
        if (serverIP.isEmpty() || portStr.isEmpty()) {
            showMessage("Preencha o IP e a porta do servidor!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            int port = Integer.parseInt(portStr);
            socket = new Socket(serverIP, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            logMessage("[INFO] Conectado ao servidor em " + serverIP + ":" + port);
            
            // Handshake
            Map<String, Object> handshake = new LinkedHashMap<>();
            handshake.put("operacao", "conectar");
            String handshakeJson = mapper.writeValueAsString(handshake);
            
            logMessage("[ENVIADO] " + handshakeJson);
            out.println(handshakeJson);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            try {
                Validator.validateServer(response);
                Map<String, Object> respMap = mapper.readValue(response, Map.class);
                boolean status = (Boolean) respMap.getOrDefault("status", false);
                
                if (status) {
                    isConnected = true;
                    btnConnect.setEnabled(false);
                    txtServerIP.setEnabled(false);
                    txtServerPort.setEnabled(false);
                    showMessage("Conectado com sucesso ao servidor!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    String info = (String) respMap.getOrDefault("info", "Erro ao conectar");
                    showMessage("Falha: " + info, "Erro", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                logMessage("[ERRO] Handshake inválido: " + e.getMessage());
                showMessage("Erro no handshake: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (NumberFormatException e) {
            showMessage("Porta inválida! Use apenas números.", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            logMessage("[ERRO] Falha ao conectar: " + e.getMessage());
            showMessage("Erro ao conectar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void criarUsuario() {
        if (!isConnected) {
            showMessage("Conecte-se ao servidor primeiro!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String cpf = txtCPFCadastro.getText().trim();
        String nome = txtNomeCadastro.getText().trim();
        String senha = new String(txtSenhaCadastro.getPassword());
        
        if (cpf.isEmpty() || nome.isEmpty() || senha.isEmpty()) {
            showMessage("Preencha todos os campos!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_criar");
            msg.put("cpf", cpf);
            msg.put("nome", nome);
            msg.put("senha", senha);
            
            String json = mapper.writeValueAsString(msg);
            Validator.validateClient(json);
            
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            processResponse(response, "Criar Usuário");
            
            // Limpar campos
            txtCPFCadastro.setText("");
            txtNomeCadastro.setText("");
            txtSenhaCadastro.setText("");
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fazerLogin() {
        if (!isConnected) {
            showMessage("Conecte-se ao servidor primeiro!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String cpf = txtCPFLogin.getText().trim();
        String senha = new String(txtSenhaLogin.getPassword());
        
        if (cpf.isEmpty() || senha.isEmpty()) {
            showMessage("Preencha CPF e senha!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_login");
            msg.put("cpf", cpf);
            msg.put("senha", senha);
            
            String json = mapper.writeValueAsString(msg);
            Validator.validateClient(json);
            
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            Validator.validateServer(response);
            Map<String, Object> respMap = mapper.readValue(response, Map.class);
            boolean status = (Boolean) respMap.getOrDefault("status", false);
            String info = (String) respMap.getOrDefault("info", "");
            
            if (status && respMap.containsKey("token")) {
                token = (String) respMap.get("token");
                logMessage("[TOKEN] Login bem-sucedido. Token armazenado.");
                
                // Habilitar abas
                tabbedPane.setEnabledAt(1, true);
                tabbedPane.setEnabledAt(2, true);
                btnLogin.setEnabled(false);
                btnLogout.setEnabled(true);
                
                showMessage("Login realizado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                
                // Consultar dados automaticamente
                consultarDados();
            } else {
                showMessage("Erro no login: " + info, "Erro", JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fazerLogout() {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_logout");
            msg.put("token", token);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            token = "";
            tabbedPane.setEnabledAt(1, false);
            tabbedPane.setEnabledAt(2, false);
            tabbedPane.setSelectedIndex(0);
            btnLogin.setEnabled(true);
            btnLogout.setEnabled(false);
            lblSaldoAtual.setText("R$ 0,00");
            
            processResponse(response, "Logout");
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void consultarDados() {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_ler");
            msg.put("token", token);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            Validator.validateServer(response);
            Map<String, Object> respMap = mapper.readValue(response, Map.class);
            boolean status = (Boolean) respMap.getOrDefault("status", false);
            
            if (status && respMap.containsKey("usuario")) {
                Map<String, Object> usuario = (Map<String, Object>) respMap.get("usuario");
                double saldo = ((Number) usuario.get("saldo")).doubleValue();
                lblSaldoAtual.setText(String.format("R$ %.2f", saldo));
                
                String dados = "\n=== DADOS DO USUÁRIO ===\n" +
                        "CPF: " + usuario.get("cpf") + "\n" +
                        "Nome: " + usuario.get("nome") + "\n" +
                        "Saldo: R$ " + String.format("%.2f", saldo) + "\n" +
                        "========================\n";
                logMessage(dados);
                
                showMessage(dados, "Dados da Conta", JOptionPane.INFORMATION_MESSAGE);
            } else {
                String info = (String) respMap.getOrDefault("info", "Erro ao consultar dados");
                showMessage("Erro: " + info, "Erro", JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void atualizarSaldo() {
        consultarDados();
    }

    private void atualizarDados() {
        String novoNome = txtNovoNome.getText().trim();
        String novaSenha = new String(txtNovaSenha.getPassword());
        
        if (novoNome.isEmpty() && novaSenha.isEmpty()) {
            showMessage("Preencha pelo menos um campo para atualizar!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            Map<String, Object> usuario = new LinkedHashMap<>();
            if (!novoNome.isEmpty()) usuario.put("nome", novoNome);
            if (!novaSenha.isEmpty()) usuario.put("senha", novaSenha);
            
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_atualizar");
            msg.put("token", token);
            msg.put("usuario", usuario);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            processResponse(response, "Atualizar Dados");
            
            // Limpar campos
            txtNovoNome.setText("");
            txtNovaSenha.setText("");
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro ao atualizar dados: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deletarConta() {
        int confirm = JOptionPane.showConfirmDialog(this, 
                "Tem certeza que deseja deletar sua conta?\nEsta ação não pode ser desfeita!", 
                "Confirmar Exclusão", 
                JOptionPane.YES_NO_OPTION, 
                JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "usuario_deletar");
            msg.put("token", token);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            processResponse(response, "Deletar Conta");
            
            // Fazer logout após deletar
            token = "";
            tabbedPane.setEnabledAt(1, false);
            tabbedPane.setEnabledAt(2, false);
            tabbedPane.setSelectedIndex(0);
            btnLogin.setEnabled(true);
            btnLogout.setEnabled(false);
            lblSaldoAtual.setText("R$ 0,00");
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void depositar() {
        String valorStr = txtValorDeposito.getText().trim().replace(",", ".");
        
        if (valorStr.isEmpty()) {
            showMessage("Informe o valor do depósito!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            double valor = Double.parseDouble(valorStr);
            if (valor <= 0) {
                showMessage("O valor deve ser maior que zero!", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "depositar");
            msg.put("token", token);
            msg.put("valor_enviado", valor);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            processResponse(response, "Depósito");
            
            // Atualizar saldo
            atualizarSaldo();
            
            // Limpar campo
            txtValorDeposito.setText("");
            
        } catch (NumberFormatException e) {
            showMessage("Valor inválido! Use formato: 100.50", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enviarPix() {
        String cpfDestino = txtCPFDestino.getText().trim();
        String valorStr = txtValorPix.getText().trim().replace(",", ".");
        
        if (cpfDestino.isEmpty() || valorStr.isEmpty()) {
            showMessage("Preencha o CPF destino e o valor!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            double valor = Double.parseDouble(valorStr);
            if (valor <= 0) {
                showMessage("O valor deve ser maior que zero!", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "transacao_criar");
            msg.put("token", token);
            msg.put("cpf_destino", cpfDestino);
            msg.put("valor", valor);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            Validator.validateServer(response);
            Map<String, Object> respMap = mapper.readValue(response, Map.class);
            boolean status = (Boolean) respMap.getOrDefault("status", false);
            String info = (String) respMap.getOrDefault("info", "");
            
            if (status) {
                showMessage("PIX enviado com sucesso!\n" + info, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                atualizarSaldo();
                txtCPFDestino.setText("");
                txtValorPix.setText("");
            } else {
                showMessage("Erro na transferência: " + info, "Erro", JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (NumberFormatException e) {
            showMessage("Valor inválido! Use formato: 100.50", "Erro", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro na transferência: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void consultarExtrato() {
        String dataInicial = txtDataInicial.getText().trim();
        String dataFinal = txtDataFinal.getText().trim();
        
        if (dataInicial.isEmpty() || dataFinal.isEmpty()) {
            showMessage("Preencha as datas inicial e final!", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("operacao", "transacao_ler");
            msg.put("token", token);
            msg.put("data_inicial", dataInicial);
            msg.put("data_final", dataFinal);
            
            String json = mapper.writeValueAsString(msg);
            logMessage("[ENVIADO] " + json);
            out.println(json);
            
            String response = in.readLine();
            logMessage("[RECEBIDO] " + response);
            
            Validator.validateServer(response);
            Map<String, Object> respMap = mapper.readValue(response, Map.class);
            boolean status = (Boolean) respMap.getOrDefault("status", false);
            
            if (status && respMap.containsKey("transacoes")) {
                Object transVal = respMap.get("transacoes");
                StringBuilder extrato = new StringBuilder("\n=== EXTRATO ===\n");
                
                try {
                    java.util.List<?> transacoes = null;
                    if (transVal instanceof java.util.List) {
                        transacoes = (java.util.List<?>) transVal;
                    } else if (transVal instanceof Object[]) {
                        transacoes = Arrays.asList((Object[]) transVal);
                    }
                    
                    if (transacoes == null || transacoes.isEmpty()) {
                        extrato.append("Nenhuma transação encontrada neste período.\n");
                    } else {
                        for (Object t : transacoes) {
                            Map<?, ?> mapT = (Map<?, ?>) t;
                            extrato.append("ID: ").append(mapT.get("id"))
                                    .append(" | Valor: R$ ").append(String.format("%.2f", ((Number) mapT.get("valor_enviado")).doubleValue()))
                                    .append("\nDe: ").append(((Map<?, ?>) mapT.get("usuario_enviador")).get("nome"))
                                    .append(" | Para: ").append(((Map<?, ?>) mapT.get("usuario_recebedor")).get("nome"))
                                    .append("\nData: ").append(mapT.get("criado_em"))
                                    .append("\n---\n");
                        }
                    }
                } catch (Exception errPrint) {
                    extrato.append("Erro ao processar extrato: ").append(errPrint.getMessage()).append("\n");
                }
                
                extrato.append("===============\n");
                logMessage(extrato.toString());
                
                JTextArea textArea = new JTextArea(extrato.toString());
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(500, 400));
                JOptionPane.showMessageDialog(this, scrollPane, "Extrato", JOptionPane.INFORMATION_MESSAGE);
            } else {
                String info = (String) respMap.getOrDefault("info", "Erro ao consultar extrato");
                showMessage("Erro: " + info, "Erro", JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception e) {
            logMessage("[ERRO] " + e.getMessage());
            showMessage("Erro ao consultar extrato: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void processResponse(String response, String operacao) {
        try {
            Validator.validateServer(response);
            Map<String, Object> respMap = mapper.readValue(response, Map.class);
            boolean status = (Boolean) respMap.getOrDefault("status", false);
            String info = (String) respMap.getOrDefault("info", "");
            
            if (status) {
                showMessage(info, operacao + " - Sucesso", JOptionPane.INFORMATION_MESSAGE);
            } else {
                showMessage(info, operacao + " - Erro", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            showMessage("Erro ao processar resposta: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logMessage(String message) {
        txtMessages.append(message + "\n");
        txtMessages.setCaretPosition(txtMessages.getDocument().getLength());
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}
