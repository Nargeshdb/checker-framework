import org.checkerframework.checker.mustcall.qual.*;

class OwningField {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
    void b() {}
  }

  @InheritableMustCall("dispose")
  static class FinalOwningField {
    final Foo f;

    FinalOwningField() {
      // :: warning: (required.method.not.called)
      f = new Foo();
      f.b();
    }

    void dispose() {
      f.a();
    }
  }
}
