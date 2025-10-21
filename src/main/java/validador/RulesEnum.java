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
    USUARIO_LER("usuario_ler"), // É literalmente o envio de um 'getUsuario()' para o servidor
    USUARIO_ATUALIZAR("usuario_atualizar"),
    USUARIO_DELETAR("usuario_deletar"),

    // CR(UD) da transação
    TRANSACAO_CRIAR("transacao_criar"),
    TRANSACAO_LER("transacao_ler"), // É literalmente o envio de um 'getTransacao()'
    DEPOSITAR("depositar");

    private final String rule;

    RulesEnum(String rule) {
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }

    public static RulesEnum getEnum(String value) {
        for (RulesEnum v : RulesEnum.values()) {
            if (Objects.equals(v.getRule(), value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Nenhuma regra encontrada para o valor: " + value);
    }
}
