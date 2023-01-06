import org.checkerframework.checker.mustcall.qual.*;
class Inference {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("dispose")
  static class OwningField {
    final Foo f;

    OwningField(Foo foo) {
      f = foo;
    }
    void dispose() {
      f.a();
    }
  }
}
