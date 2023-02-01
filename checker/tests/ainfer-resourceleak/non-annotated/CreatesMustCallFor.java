import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;

class CreatesMustCallFor {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("a")
  static class NonFinalOwningField {
    Foo f; // expect owning annotation for this field

    void initialyzeFooWithoutCMCF() {
      f.a();
      f = new Foo();
    }

    void initialyzeFooWithoutCMCF2() {
      // :: warning: (required.method.not.called)
      f = new Foo();
    }

    @EnsuresCalledMethods(value = { "this.f" }, methods = { "a" })
    void a() {
      f.a();
    }
  }
}
