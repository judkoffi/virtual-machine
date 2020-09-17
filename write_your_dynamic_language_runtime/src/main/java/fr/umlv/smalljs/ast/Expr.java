package fr.umlv.smalljs.ast;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface Expr {
  int lineNumber();

  sealed interface Instr {
    // maker interface for expressions that return void
  }

  record Block(List<Expr> instrs, int lineNumber) implements Expr, Instr {
    public Block {
      requireNonNull(instrs);
      lineNumber = instrs.stream().mapToInt(Expr::lineNumber).findFirst().orElse(lineNumber);
    }
  }

  record FieldAccess(Expr receiver, String name, int lineNumber) implements Expr {
    public FieldAccess {
      requireNonNull(receiver);
      requireNonNull(name);
    }
  }

  record FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) implements Expr, Instr {
    public FieldAssignment {
      requireNonNull(receiver);
      requireNonNull(name);
      requireNonNull(expr);
    }
  }

  record Fun(Optional<String> name, List<String> parameters, Block body, int lineNumber) implements Expr {
    public Fun {
      requireNonNull(name);
      requireNonNull(parameters);
      requireNonNull(body);
    }
  }

  record FunCall(Expr qualifier, List<Expr> args, int lineNumber) implements Expr {
    public FunCall {
      requireNonNull(qualifier);
      requireNonNull(args);
    }
  }

  record If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) implements Expr, Instr {
    public If {
      requireNonNull(condition);
      requireNonNull(trueBlock);
      requireNonNull(falseBlock);
    }
  }

  record Literal<T>(T value, int lineNumber) implements Expr {
    public Literal {
      requireNonNull(value);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }

  record LocalVarAccess(String name, int lineNumber) implements Expr {
    public LocalVarAccess {
      requireNonNull(name);
    }
  }

  record LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) implements Expr, Instr {
    public LocalVarAssignment {
      requireNonNull(name);
      requireNonNull(expr);
    }
  }

  record MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) implements Expr {
    public MethodCall {
      requireNonNull(receiver);
      requireNonNull(name);
      requireNonNull(args);
    }
  }

  record New(Map<String, Expr> initMap, int lineNumber) implements Expr {
    // don't use Map.copyOf here because the order is not guaranteed
  }

  record Return(Expr expr, int lineNumber) implements Expr, Instr {
    public Return {
      requireNonNull(expr);
    }
  }
}
