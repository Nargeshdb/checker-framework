import org.checkerframework.checker.mustcall.qual.*;
class Inference {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  static class OwningField {
    Foo f; // expect owning annotation for this field

    void OwningField() {
      // :: warning: (required.method.not.called)
      f = new Foo();
    }
    void dispose() {
      f.a();
    }
  }
}
