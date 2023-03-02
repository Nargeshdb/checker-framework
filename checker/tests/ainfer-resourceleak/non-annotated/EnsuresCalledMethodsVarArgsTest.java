import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class EnsuresCalledMethodsVarArgsTest {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  private class ECMVA {
    final Foo ff;

    ECMVA() {
      // :: warning: (required.method.not.called)
      ff = new Foo();
    }

    void finalyzer() {
      close(ff);
    }


    @EnsuresCalledMethods(value = { "#1" }, methods = { "a" })
    void closef(Foo f) {
      if (f != null) {
        close(f);
      }
    }

    @SuppressWarnings("ensuresvarargs.unverified")
    @EnsuresCalledMethodsVarArgs("a")
    void close(Foo... foos) {
      for (Foo f : foos) {
        if (f != null) {
          f.a();
        }
      }
    }
  }

}
