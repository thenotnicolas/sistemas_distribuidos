package validador;

import java.util.Objects;

public enum RulesEnum {
    // Conectar
    CONECTAR("conectar"),
    // Login e Logoff
    USUARIO_LOGIN("usuario_login"),
    USUARIO_LOGOUT("usuario_logout"),
    // CRUD do usuário
    USUARIO_CRIAR("usuario_criar"),
    USUARIO_LER("usuario_ler"),
    USUARIO_ATUALIZAR("usuario_atualizar"),
    USUARIO_DELETAR("usuario_deletar"),
    // CR(UD) da transação
    TRANSACAO_CRIAR("transacao_criar"),
    TRANSACAO_LER("transacao_ler"),
    DEPOSITAR("depositar"),
    // Erro no servidor
    ERRO_SERVIDOR("erro_servidor");

    private final String rule;
    RulesEnum(String rule) { this.rule = rule; }
    public String getValue() { return rule; }
    public static RulesEnum getEnum(String rule){
        Objects.requireNonNull(rule, "O valor da regra não pode ser nulo.");
        for (RulesEnum e : RulesEnum.values()) {
            if(e.getValue().equalsIgnoreCase(rule)) return e;
        }
        throw new IllegalArgumentException("Nenhuma regra encontrada para o valor: " + rule);
    }
}