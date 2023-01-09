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
    void dispose() {
      if (true) {
        f.a();
      }
    }
  }

  static class NonFinalOwningField {
    Foo f; // expect owning annotation for this field

    void initialyzeFoo() {
      // :: warning: (required.method.not.called)
      f = new Foo();
    }

    void dispose() {
      f.a();
    }
  }
}
