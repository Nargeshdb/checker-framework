import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;

class CreatesMustCallFor {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  static class NonFinalOwningField {
    // :: warning: (required.method.not.called)
    @Owning Foo f; // expect owning annotation for this field

    // :: warning: (missing.creates.mustcall.for)
    void initialyzeFooWithCMCF() {
      // :: warning: (required.method.not.called)
      f = new Foo();
    }

    // :: warning: (missing.creates.mustcall.for)
    void initialyzeFooWithoutCMCF() {
      f.a();
      f = new Foo();
      f.a();
    }

    void dispose() {
      f.a();
    }
  }
}
