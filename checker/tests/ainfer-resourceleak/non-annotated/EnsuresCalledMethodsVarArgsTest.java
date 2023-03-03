import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class EnsuresCalledMethodsVarArgsTest {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  static class Utils {
    @SuppressWarnings("ensuresvarargs.unverified")
    @EnsuresCalledMethodsVarArgs("a")
    public static void close(Foo... foos) {
      for (Foo f : foos) {
        if (f != null) {
          f.a();
        }
      }
    }
  }

  private class ECMVA {
    final Foo fff;

    ECMVA() {
      // :: warning: (required.method.not.called)
      fff = new Foo();
    }

    void finalyzer() {
      Utils.close(fff);
    }


    @EnsuresCalledMethods(value = { "#1" }, methods = { "a" })
    void closef(Foo f) {
      if (f != null) {
        Utils.close(f);
      }
    }


  }

  private class ECMVACorrect {
    final Foo ff;

    ECMVACorrect() {
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

    @EnsuresCalledMethods(value = { "#1" }, methods = { "a" })
    void close(Foo f) {
      if (f != null) {
        f.a();
      }
    }
  }

}
