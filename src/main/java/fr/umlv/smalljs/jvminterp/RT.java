package fr.umlv.smalljs.jvminterp;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.invoker;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

public final class RT {
  private static final MethodHandle LOOKUP_OR_DEFAULT, LOOKUP_OR_FAIL, REGISTER, INVOKE, TRUTH, LOOKUP_MH;
  static {
    var lookup = MethodHandles.lookup();
    try {
      LOOKUP_OR_DEFAULT = lookup.findVirtual(JSObject.class, "lookupOrDefault", methodType(Object.class, String.class, Object.class));
      LOOKUP_OR_FAIL = lookup.findStatic(RT.class, "lookupOrFail", methodType(Object.class, JSObject.class, String.class));
      REGISTER = lookup.findVirtual(JSObject.class, "register", methodType(void.class, String.class, Object.class));

      INVOKE = lookup.findVirtual(JSObject.class, "invoke", methodType(Object.class, Object.class, Object[].class));

      TRUTH = lookup.findStatic(RT.class, "truth", methodType(boolean.class, Object.class));

      LOOKUP_MH = lookup.findStatic(RT.class, "lookupMethodHandle", methodType(MethodHandle.class, JSObject.class, String.class));
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

  @SuppressWarnings("unused")  // used by a method handle
  private static Object lookupOrFail(JSObject jsObject, String key) {
    var value = jsObject.lookupOrDefault(key, null);
    if (value == null) {
      throw new Failure("no value for " + key);
    }
    return value;
  }

  public static CallSite bsm_lookup(Lookup lookup, String name, MethodType type, String variableName) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.global();
    // get the LOOKUP_OR_FAIL method handle
    var mh = LOOKUP_OR_FAIL;
    // use the global environment as first argument and the variableName as second argument
    mh = MethodHandles.insertArguments(mh, 0, globalEnv, variableName);
    // create a constant callsite
    return new ConstantCallSite(mh);
  }

//  public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
//    // get INVOKE method handle
//    var mh = INVOKE;
//    // make it accept an Object (not a JSObject) and objects as other parameters
//    mh = mh.asType(type);
//    // create a constant callsite
//    return new ConstantCallSite(mh);
//  }

  public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
    return new InliningCache(type);
  }

  private static final class InliningCache extends MutableCallSite {
    private static final MethodHandle SLOW_PATH, POINTER_CHECK;
    static {
      var lookup = MethodHandles.lookup();
      try {
        SLOW_PATH = lookup.findVirtual(InliningCache.class, "slowPath", methodType(MethodHandle.class, Object.class, Object.class));
        POINTER_CHECK = lookup.findStatic(InliningCache.class, "pointerCheck", methodType(boolean.class, Object.class, Object.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    public InliningCache(MethodType type) {
      super(type);
      setTarget(MethodHandles.foldArguments(MethodHandles.exactInvoker(type), SLOW_PATH.bindTo(this)));
    }

    private static boolean pointerCheck(Object o1, Object o2) {
      return o1 == o2;
    }

    private MethodHandle slowPath(Object qualifier, Object receiver) {
      var jsObject = (JSObject) qualifier;
      var mh = jsObject.methodHandle();
      var isVarargs = mh.isVarargsCollector();

      if (!mh.isVarargsCollector() && type().parameterCount() - 2 != mh.type().parameterCount() - 1) {
        throw new Failure("arguments doesn't match parameters count " + (type().parameterCount() - 2) + " " + (mh.type().parameterCount() - 1));
      }

      mh = MethodHandles.dropArguments(mh, 0, Object.class)
              .withVarargs(isVarargs);
      var target = mh.asType(type());

      var test = POINTER_CHECK.bindTo(jsObject);
//      test = MethodHandles.insertArguments(test, 1, jsObject); // Same as `POINTER_CHECK.bindTo(jsObject)`
      var fallback = getTarget();
      var guard = MethodHandles.guardWithTest(test, target, fallback);
      setTarget(guard);

      // atm : if b -> if a -> ...
      // todo : if a -> if b -> ... (1 line of code)

      return target;
    }
  }

  public static CallSite bsm_globalcall(Lookup lookup, String name, MethodType type, String variableName) {
    throw new UnsupportedOperationException("TODO bsm_globalcall");
    //var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    //var globalEnv = classLoader.global();
    // TODO
  }

  public static Object bsm_fun(Lookup lookup, String name, Class<?> type, int funId) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.global();
    // get the dictionary and get the Fun object corresponding to the id
    var dictionary = classLoader.dictionary();
    var fun = dictionary.lookupAndClear(funId);
    // create the function using ByteCodeRewriter.createFunction(...)
    return ByteCodeRewriter.createFunction(name, fun.parameters(), fun.body(), globalEnv);
  }

  public static CallSite bsm_register(Lookup lookup, String name, MethodType type, String functionName) {
    var classLoader = (FunClassLoader) lookup.lookupClass().getClassLoader();
    var globalEnv = classLoader.global();
    // get the REGISTER method handle
    var mh = REGISTER;
    // use the global environment as first argument and the functionName as second argument
    var target = MethodHandles.insertArguments(mh, 0, globalEnv, functionName);
    // create a constant callsite
    return new ConstantCallSite(target);
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static boolean truth(Object o) {
    return o != null && o != UNDEFINED && o != Boolean.FALSE;
  }
  public static CallSite bsm_truth(Lookup lookup, String name, MethodType type) {
    // get the TRUTH method handle
    var mh = TRUTH;
    // create a constant callsite
    return new ConstantCallSite(mh);
  }

  public static CallSite bsm_get(Lookup lookup, String name, MethodType type, String fieldName) {
    throw new UnsupportedOperationException("TODO bsm_get");
    // get the LOOKUP_OR_DEFAULT method handle
    // use the fieldName and UNDEFINED as second argument and third argument
    // make it accept an Object (not a JSObject) as first parameter
    // create a constant callsite
  }

  public static CallSite bsm_set(Lookup lookup, String name, MethodType type, String fieldName) {
    throw new UnsupportedOperationException("TODO bsm_set");
    // get the REGISTER method handle
    // use the fieldName as second argument
    // make it accept an Object (not a JSObject) as first parameter
    // create a constant callsite
  }

  @SuppressWarnings("unused")  // used by a method handle
  private static MethodHandle lookupMethodHandle(JSObject receiver, String fieldName) {
    var function = (JSObject) receiver.lookupOrDefault(fieldName, null);
    if (function == null) {
      throw new Failure("no method " + fieldName);
    }
    return function.methodHandle();
  }

  public static CallSite bsm_methodcall(Lookup lookup, String name, MethodType type) {
    throw new UnsupportedOperationException("TODO bsm_methodcall");
    //var combiner = insertArguments(METH_LOOKUP_MH, 1, name).asType(methodType(MethodHandle.class, Object.class));
    //var target = foldArguments(invoker(type), combiner);
    //return new ConstantCallSite(target);
  }
}
