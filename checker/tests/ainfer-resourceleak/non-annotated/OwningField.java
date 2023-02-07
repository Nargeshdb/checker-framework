import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class OwningField {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  static class FinalOwningField {
    final Foo f; // expect owning annotation for this field

    FinalOwningField() {
      // :: warning: (required.method.not.called)
      f = new Foo();
    }

    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    void dispose() {
      f.a();
    }
  }

  @InheritableMustCall("dispose")
  static class NonFinalOwningField {
    Foo f; // expect owning annotation for this field

    @SuppressWarnings("missing.creates.mustcall.for")
    void initialyzeFoo() {
      f.a();
      // :: warning: (required.method.not.called)
      f = new Foo();
    }

    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    void dispose() {
      f.a();
    }
  }
}
