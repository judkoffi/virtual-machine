package fr.umlv.smalljs.stackinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.*;
import fr.umlv.smalljs.ast.VoidVisitor;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static fr.umlv.smalljs.stackinterp.Instructions.*;
import static fr.umlv.smalljs.stackinterp.TagValues.encodeDictObject;
import static fr.umlv.smalljs.stackinterp.TagValues.encodeSmallInt;

public class InstrRewriter {
  static class InstrBuffer {
    private int[] instrs;
    private int size;

    InstrBuffer() {
      instrs = new int[32];
    }

    InstrBuffer emit(int value) {
      if (size == instrs.length) {
        instrs = Arrays.copyOf(instrs, size << 1);
      }
      instrs[size++] = value;
      return this;
    }

    int label() {
      return size;
    }

    int placeholder() {
      return size++;
    }

    void patch(int position, int label) {
      instrs[position] = label;
    }

    int[] toInstrs() {
      return Arrays.copyOf(instrs, size);
    }
  }

  private InstrRewriter(Dictionary dict, InstrBuffer buffer, JSObject globalEnv) {
    this.visitor = createVisitor(buffer, dict, globalEnv);
  }

  public static JSObject createFunction(Optional<String> name, List<String> parameters, Block body, Dictionary dict, JSObject globalEnv) {
    var env = JSObject.newEnv(null);

    env.register("this", 0);
    for (var parameter : parameters) {
      env.register(parameter, env.length());
    }
    visitVariable(body, env);

    var buffer = new InstrBuffer();
    var rewriter = new InstrRewriter(dict, buffer, globalEnv);
    rewriter.rewrite(body, env);
    buffer.emit(CONST)
      .emit(encodeDictObject(UNDEFINED, dict));
    buffer.emit(RET);

    var instrs = buffer.toInstrs();
    Instructions.dump(instrs, dict);

    var code = new Code(instrs, parameters.size() + 1 /* this */, env.length());
    var function = JSObject.newFunction(name.orElse("lambda"), (self, receiver, args) -> {
      if (receiver != UNDEFINED || args.length != 0) {
        throw new Failure("can not interpret a function with a receiver and/or arguments");
      }
      return StackInterpreter.execute(self, dict, globalEnv);
    });
    function.register("__code__", code);
    return function;
  }

  private static void visitVariable(Expr expr, JSObject env) {
    VARIABLE_VISITOR.visit(expr, env);
  }

  private static final VoidVisitor<JSObject> VARIABLE_VISITOR = new VoidVisitor<JSObject>()
    .when(Block.class, (block, env) -> {
      for (var instr : block.instrs()) {
        visitVariable(instr, env);
      }
    })
    .when(Literal.class, (literal, env) -> {
      // do nothing
    })
    .when(FunCall.class, (funCall, env) -> {
      // do nothing
    })
    .when(LocalVarAssignment.class, (localVarAssignment, env) -> {
      if (localVarAssignment.declaration()) {
        env.register(localVarAssignment.name(), env.length());
      }
    })
    .when(LocalVarAccess.class, (localVarAccess, env) -> {
      // do nothing
    })
    .when(Fun.class, (fun, env) -> {
      // do nothing
    })
    .when(Return.class, (_return, env) -> {
      // do nothing
    })
    .when(If.class, (_if, env) -> {
      visitVariable(_if.trueBlock(), env);
      visitVariable(_if.falseBlock(), env);
    })
    .when(New.class, (_new, env) -> {
      // do nothing
    })
    .when(FieldAccess.class, (fieldAccess, env) -> {
      // do nothing
    })
    .when(FieldAssignment.class, (fieldAssignment, env) -> {
      // do nothing
    })
    .when(MethodCall.class, (methodCall, env) -> {
      // do nothing
    });

  private void rewrite(Expr expr, JSObject env) {
    visitor.visit(expr, env);
  }

  public static VoidVisitor<JSObject> createVisitor(InstrBuffer buffer, Dictionary dict, JSObject globalEnv) {
    var visitor = new VoidVisitor<JSObject>();
    visitor.when(Block.class, (block, env) -> {
      // for each expression of the block
      for (var expr : block.instrs()) {
        // visit the expression
        visitor.visit(expr, env);
        // if the expression is an instruction (i.e. return void)
        if (!(expr instanceof Instr)) {
          // ask to top the top of the stack
          buffer.emit(POP);
        }
      }
    })
      .when(Literal.class, (literal, env) -> {
        // throw new UnsupportedOperationException("TODO Literal");
        //  get the literal value
        var value = literal.value();
        // test if it's a positive integers
        if (value instanceof Integer && ((Integer) value) >= 0) {
          // emit a small int
          buffer//
            .emit(CONST)
            .emit(encodeSmallInt((int) value));
        } else {
          // emit a dictionary object
          buffer//
            .emit(CONST)
            .emit(encodeDictObject(value, dict));
        }
      })
      .when(FunCall.class, (funCall, env) -> {
        // throw new UnsupportedOperationException("TODO FunCall");
        // visit the qualifier
        visitor.visit(funCall.qualifier(), env);
        // emit undefined
        buffer//
          .emit(CONST)
          .emit(encodeDictObject(UNDEFINED, dict));
        // visit all arguments
        for (var arg : funCall.args()) {
          visitor.visit(arg, env);
        }
        // emit the funcall
        buffer//
          .emit(FUNCALL)
          .emit(funCall.args()
            .size());
      })
      .when(LocalVarAccess.class, (localVarAccess, env) -> {
        //  throw new UnsupportedOperationException("TODO LocalVarAccess");
        // get the local variable name
        var name = localVarAccess.name();
        // find if there is a local variable in the environment with the name
        var slotOrUndefined = env.lookup(name);
        if (slotOrUndefined == UNDEFINED) {
          // emit a lookup with the name
          buffer//
            .emit(LOOKUP)
            .emit(encodeDictObject(name, dict));
        } else {
          // load the local variable with the slot
          buffer//
            .emit(LOAD)
            .emit((int) slotOrUndefined);
        }
      })
      .when(LocalVarAssignment.class, (localVarAssignment, env) -> {
//        throw new UnsupportedOperationException("TODO LocalVarAssignment");
        // visit the expression
        visitor.visit(localVarAssignment.expr(), env);
        // get the local variable name
        var name = localVarAssignment.name();
        // find if there is a local variable in the env from the name
        var slotOrUndefined = env.lookup(name);
        if (slotOrUndefined == UNDEFINED) {
          throw new Failure("unknown local variable " + name);
        }
        // emit a store at the variable slot
        buffer//
          .emit(STORE)
          .emit((int) slotOrUndefined);
      })
      .when(Fun.class, (fun, env) -> {
        //throw new UnsupportedOperationException("TODO Fun");
        // create a JSObject function
        var function = createFunction(fun.name(), fun.parameters(), fun.body(), dict, globalEnv);
        // emit a const on the function
        buffer//
          .emit(CONST)
          .emit(encodeDictObject(function, dict));
        // if the name is present emit a code to register the function in the global environment
        fun.name()
          .ifPresent(name -> {
            buffer.emit(DUP);
            buffer//
              .emit(REGISTER)
              .emit(encodeDictObject(name, dict));
          });
      })
      .when(Return.class, (_return, env) -> {
        //throw new UnsupportedOperationException("TODO Return");
        // emit a visit of the expression
        visitor.visit(_return.expr(), env);
        // emit a RET
        buffer.emit(RET);
      })
      .when(If.class, (_if, env) -> {
        //throw new UnsupportedOperationException("TODO If");
        // visit the condition
        visitor.visit(_if.condition(), env);
        // emit a JUMP_IF_FALSE and a placeholder
        var falsePlaceHolder = buffer.emit(JUMP_IF_FALSE)
          .placeholder();
        // visit the true block
        visitor.visit(_if.trueBlock(), env);
        // emit a goto with another placeholder
        var endPlaceHolder = buffer.emit(GOTO)
          .placeholder();
        // patch the first placeholder
        buffer.patch(falsePlaceHolder, buffer.label());
        // visit the false block
        visitor.visit(_if.falseBlock(), env);
        // patch the second place holder
        buffer.patch(endPlaceHolder, buffer.label());
      })
      .when(New.class, (_new, env) -> {
        //throw new UnsupportedOperationException("TODO New");
        // create a JSObject class
        var clazz = JSObject.newObject(null);
        // loop over all the field initializations
        _new//
          .initMap()
          .forEach((fieldName, expr) -> {
            // register the field name with the right slot
            clazz.register(fieldName, clazz.length());
            // visit the initialization expression
            visitor.visit(expr, env);
          });
        // emit a NEW with the class
        buffer//
          .emit(NEW)
          .emit(encodeDictObject(clazz, dict));
      })
      .when(FieldAccess.class, (fieldAccess, env) -> {
        // throw new UnsupportedOperationException("TODO FieldAccess");
        // visit the receiver
        visitor.visit(fieldAccess.receiver(), env);
        // emit a GET with the field name
        buffer.emit(GET)
          .emit(encodeDictObject(fieldAccess.name(), dict));
      })
      .when(FieldAssignment.class, (fieldAssignment, env) -> {
//        throw new UnsupportedOperationException("TODO FieldAssignment");
        // visit the receiver
        visitor.visit(fieldAssignment.receiver(), env);
        // visit the expression
        visitor.visit(fieldAssignment.expr(), env);
        // emit a PUT with the field name
        buffer.emit(PUT)
          .emit(encodeDictObject(fieldAssignment.name(), dict));
      })
      .when(MethodCall.class, (methodCall, env) -> {
        //throw new UnsupportedOperationException("TODO MethodCall");
        // visit the receiver
        visitor.visit(methodCall.receiver(), env);
        // emit a DUP, get the field name and emit a SWAP of the qualifier and the receiver
        buffer.emit(DUP);
        buffer.emit(GET)
          .emit(encodeDictObject(methodCall.name(), dict));
        buffer.emit(SWAP);
        // visit all arguments
        for (var arg : methodCall.args()) {
          visitor.visit(arg, env);
        }
        // emit the funcall
        buffer.emit(FUNCALL)
          .emit(methodCall.args()
            .size());
      });
    return visitor;
  }

  private final VoidVisitor<JSObject> visitor;
}
