package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.*;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.ast.Visitor;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;

public class ASTInterpreter {
  private static <T> T as(Object value, Class<T> type, Expr failedExpr) {
    try {
      return type.cast(value);
    } catch (@SuppressWarnings("unused") ClassCastException e) {
      throw new Failure("at line " + failedExpr.lineNumber() + ", type error " + value + " is not a " + type.getSimpleName());
    }
  }

  static Object visit(Expr expr, JSObject env) {
    return VISITOR.visit(expr, env);
  }

  private static final Visitor<JSObject, Object> VISITOR =
    new Visitor<JSObject, Object>()
      .when(Block.class, (block, env) -> {
        block.instrs().forEach((b) -> visit(b, env));
        return UNDEFINED;
      })
      .when(Literal.class, (literal, env) -> literal.value())
      .when(FunCall.class, (funCall, env) -> {
        var value = visit(funCall.qualifier(), env);
        var print = as(value, JSObject.class, funCall);
        var parameters = funCall.args().stream().map(e -> visit(e, env)).toArray();
        return print.invoke(UNDEFINED, parameters);
      })
      .when(LocalVarAccess.class, (localVarAccess, env) -> env.lookup(localVarAccess.name()))
      .when(LocalVarAssignment.class, (localVarAssignment, env) -> {
        var value = env.lookup(localVarAssignment.name());
        if (!localVarAssignment.declaration() && value == UNDEFINED) {
          throw new Failure("no variable " + localVarAssignment.name() + " defined");
        }

        if (localVarAssignment.declaration() && value != UNDEFINED) {
          throw new Failure("variable " + localVarAssignment.name() + " already defined");
        }

        var visitedValue = visit(localVarAssignment.expr(), env);
        env.register(localVarAssignment.name(), visitedValue);
        return UNDEFINED;
      })
      .when(Fun.class, (fun, env) -> {
        var functionName = fun.name().orElse("lambda");
        JSObject.Invoker invoker = (self, receiver, args) -> {
          if (fun.parameters().size() != args.length) {
            throw new Failure("wrong number of arguments at " + fun.lineNumber());
          }
          var newEnv = JSObject.newEnv(env);
          newEnv.register("this", receiver);
          var params = fun.parameters();
          for (var i = 0; i < params.size(); i++) {
            newEnv.register(params.get(i), args[i]);
          }
          try {
            return visit(fun.body(), newEnv);
          } catch (ReturnError error) {
            return error.getValue();
          }
        };
        var result = JSObject.newFunction(functionName, invoker);
        fun.name().ifPresent(name -> env.register(name, result));
        return result;
      })
      .when(Return.class, (_return, env) -> {
        var value = visit(_return.expr(), env);
        throw new ReturnError(value);
      })
      .when(If.class, (_if, env) -> {
        var result = visit(_if.condition(), env);
        var value = as(result, Integer.class, _if);
        return (value == 0)
          ? visit(_if.falseBlock(), env)
          : visit(_if.trueBlock(), env);
      })
      .when(New.class, (_new, env) -> {
        var newObj = JSObject.newObject(null);
        _new.initMap().forEach((k, v) -> {
          var value = visit(v, env);
          newObj.register(k, value);
        });
        return newObj;
      })
      .when(FieldAccess.class, (fieldAccess, env) -> {
        var value = visit(fieldAccess.receiver(), env);
        var receiver = as(value, JSObject.class, fieldAccess);
        return receiver.lookup(fieldAccess.name());
      })
      .when(FieldAssignment.class, (fieldAssignment, env) -> {
        var value = visit(fieldAssignment.receiver(), env);
        var receiver = as(value, JSObject.class, fieldAssignment);
        var newFieldValue = visit(fieldAssignment.expr(), env);
        receiver.register(fieldAssignment.name(), newFieldValue);
        return UNDEFINED;
      })
      .when(MethodCall.class, (methodCall, env) -> {
        throw new UnsupportedOperationException("TODO MethodCall");
      });

  @SuppressWarnings("unchecked")
  public static void interpret(Script script, PrintStream outStream) {
    JSObject globalEnv = JSObject.newEnv(null);
    Block body = script.body();
    globalEnv.register("global", globalEnv);
    globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
      System.err.println("print called with " + Arrays.toString(args));
      outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
      return UNDEFINED;
    }));
    globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
    globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
    globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
    globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
    globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));

    globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("<", JSObject.newFunction("<", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
    globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
    globalEnv.register(">", JSObject.newFunction(">", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
    globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
    visit(body, globalEnv);
  }
}

