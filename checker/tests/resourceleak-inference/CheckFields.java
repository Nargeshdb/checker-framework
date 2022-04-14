import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
import org.checkerframework.checker.mustcall.qual.MustCall;
// @MustCall("a")

class CheckFields {
  final Foo checkFieldsFoo;

  public CheckFields(Foo f) {
    this.checkFieldsFoo = f;
  }

  @EnsuresCalledMethods(
      value = {"this.checkFieldsFoo"},
      methods = {"a"})
  void checkFieldsFinalizer() {
    this.checkFieldsFoo.a();
  }

  @MustCall("a") static class Foo {
    void a() {}

    void c() {}
  }

  Foo makeFoo() {
    return new Foo();
  }
}
