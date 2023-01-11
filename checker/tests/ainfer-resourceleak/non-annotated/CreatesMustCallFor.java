import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;

class CreatesMustCallFor {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("a")
  static class NonFinalOwningField {
    @Owning Foo f; // expect owning annotation for this field

    // :: warning: (missing.creates.mustcall.for)
    void initialyzeFooWithoutCMCF() {
      f.a();
      f = new Foo();
    }

    @EnsuresCalledMethods(value = { "this.f" }, methods = { "a" })
    void a() {
      f.a();
    }
  }
}
