package fr.umlv.smalljs.jvminterp;

import fr.umlv.smalljs.rt.ArrayMap;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.invoker;
import static java.lang.invoke.MethodType.methodType;

public class RT {
  private static final MethodHandle INVOKER, LOOKUP,
    REGISTER, TRUTH, GET_MH, METH_LOOKUP_MH;

  static {
    var lookup = MethodHandles.lookup();
    try {
      INVOKER = lookup.findVirtual(JSObject.class, "invoke", methodType(Object.class, Object.class, Object[].class));
      LOOKUP = lookup.findVirtual(JSObject.class, "lookup", methodType(Object.class, String.class));
      REGISTER = lookup.findVirtual(JSObject.class, "register", methodType(void.class, String.class, Object.class));
      TRUTH = lookup.findStatic(RT.class, "truth", methodType(boolean.class, Object.class));

      GET_MH = lookup.findVirtual(JSObject.class, "getMethodHandle", methodType(MethodHandle.class));
      METH_LOOKUP_MH = lookup.findStatic(RT.class, "lookupMethodHandle", methodType(MethodHandle.class, JSObject.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  public static Object bsm_undefined(Lookup lookup, String name, Class<?> type) {
    return UNDEFINED;
  }

  public static Object bsm_const(Lookup lookup, String name, Class<?> type, int constant) {
    return constant;
  }

  /*
  public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
    //throw new UnsupportedOperationException("TODO bsm_funcall");
    var target = INVOKER.asCollector(Object[].class, type.parameterCount() - 2);
    target = target.asType(type);
    return new ConstantCallSite(target);
  }
 */

  public static CallSite bsm_lookup(Lookup lookup, String name, MethodType type, String functionName) {
    // throw new UnsupportedOperationException("TODO bsm_lookup");
    var classLoader = (FunClassLoader) lookup.lookupClass()
      .getClassLoader();
    var globalEnv = classLoader.getGlobal();
    return new ConstantCallSite(insertArguments(LOOKUP, 0, globalEnv, functionName));
  }


  public static Object bsm_fun(Lookup lookup, String name, Class<?> type, int funId) {
    //throw new UnsupportedOperationException("TODO bsm_fun");
    var classLoader = (FunClassLoader) lookup.lookupClass()
      .getClassLoader();
    var globalEnv = classLoader.getGlobal();
    var fun = classLoader.getDictionary()
      .lookupAndClear(funId);
    return ByteCodeRewriter.createFunction(fun.name()
      .orElse("lambda"), fun.parameters(), fun.body(), globalEnv);
  }

  public static CallSite bsm_register(Lookup lookup, String name, MethodType type, String functionName) {
    //throw new UnsupportedOperationException("TODO bsm_register");
    var classLoader = (FunClassLoader) lookup.lookupClass()
      .getClassLoader();
    var globalEnv = classLoader.getGlobal();
    return new ConstantCallSite(insertArguments(REGISTER, 0, globalEnv, functionName));
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static boolean truth(Object o) {
    return o != null && o != UNDEFINED && o != Boolean.FALSE;
  }

  public static CallSite bsm_truth(Lookup lookup, String name, MethodType type) {
    // throw new UnsupportedOperationException("TODO bsm_truth");
    return new ConstantCallSite(TRUTH);
  }

  public static CallSite bsm_get(Lookup lookup, String name, MethodType type, String fieldName) {
    //throw new UnsupportedOperationException("TODO bsm_get");
    return new ConstantCallSite(insertArguments(LOOKUP, 1, fieldName).asType(type));
  }

  public static CallSite bsm_set(Lookup lookup, String name, MethodType type, String fieldName) {
    //throw new UnsupportedOperationException("TODO bsm_set");
    return new ConstantCallSite(insertArguments(REGISTER, 1, fieldName).asType(type));
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static MethodHandle lookupMethodHandle(JSObject receiver, String fieldName) {
    var function = (JSObject) receiver.lookup(fieldName);
    return function.getMethodHandle();
  }

  public static CallSite bsm_methodcall(Lookup lookup, String name, MethodType type) {
    var combiner = MethodHandles.insertArguments(METH_LOOKUP_MH, 1, name)
      .asType(methodType(MethodHandle.class, Object.class));
    var target = MethodHandles.foldArguments(invoker(type), combiner);
    return new ConstantCallSite(target);
  }

  public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
    return new InliningCache(type);
  }

  private static class InliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH, CHECK;

    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningCache.class, "slowPath", methodType(Object.class, Object.class, Object.class, Object[].class));
        CHECK = lookup.findStatic(InliningCache.class, "check", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    public InliningCache(MethodType type) {
      super(type);
      setTarget(SLOW_PATH.bindTo(this)
        .asCollector(Object[].class, type.parameterCount() - 2));
    }

    private static boolean check(Object o1, Object o2) {
      return o1 == o2;
    }

    private Object slowPath(Object qualifier, Object receiver, Object[] args) {
      //var jsObject = (JSObject)qualifier;
      // return jsObject.invoke(receiver, args);

      var jsObject = (JSObject) qualifier;
      var mh = jsObject.getMethodHandle();

      if (!mh.isVarargsCollector() && args.length != mh.type()
        .parameterCount() - 1) {
        throw new Failure("arguments doesn't match parameters count " + args.length + " " + (mh.type()
          .parameterCount() - 1));
      }

      var test = CHECK.bindTo(jsObject);
      var target = MethodHandles.dropArguments(mh.asType(type().dropParameterTypes(0, 1)), 0, Object.class);
      var guard = MethodHandles.guardWithTest(test, target, new InliningCache(type()).dynamicInvoker());
      setTarget(guard);
      return jsObject.invoke(receiver, args);
    }
  }

  private static class InliningFieldCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH, LAYOUT_CHECK, FAST_ACCESS;

    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningFieldCache.class, "slowPath", methodType(Object.class, Object.class));
        LAYOUT_CHECK = lookup.findStatic(InliningFieldCache.class, "layoutCheck", methodType(boolean.class, ArrayMap.Layout.class, Object.class));
        FAST_ACCESS = lookup.findVirtual(JSObject.class, "fastAccess", methodType(Object.class, int.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final String fieldName;

    public InliningFieldCache(MethodType type, String fieldName) {
      super(type);
      this.fieldName = fieldName;
      setTarget(SLOW_PATH.bindTo(this));
    }

    @SuppressWarnings("unused")  // called by a MH
    private static boolean layoutCheck(ArrayMap.Layout layout, Object o) {
      return layout == ((JSObject) o).getLayout();
    }

    @SuppressWarnings("unused")  // called by a MH
    private Object slowPath(Object receiver) {
      var jsObject = (JSObject) receiver;

      // classical access to the value
      // var value = jsObject.lookup(fieldName);

      // fast access
      var layout = jsObject.getLayout();
      var slot = layout.slot(fieldName);   // may be -1 !
      
      if (slot == -1) {
        return UNDEFINED;
      }

      var value = jsObject.fastAccess(slot);

      //GWT
      var test = LAYOUT_CHECK.bindTo(jsObject);
      var target = MethodHandles.insertArguments(FAST_ACCESS, 1, slot)
        .asType(type());
      var guard = MethodHandles.guardWithTest(test, target, getTarget());
      return value;
    }
  }
}
