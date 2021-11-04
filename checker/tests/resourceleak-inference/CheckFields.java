import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
import org.checkerframework.checker.mustcall.qual.MustCall;
import org.checkerframework.checker.mustcall.qual.MustCallAlias;
import org.checkerframework.checker.mustcall.qual.Owning;

@MustCall("a") class CheckFields {
  private final @Owning Foo checkFieldsFoo;

  public @MustCallAlias CheckFields(@MustCallAlias Foo f) {
    this.checkFieldsFoo = f;
  }

  @EnsuresCalledMethods(
      value = {"this.checkFieldsFoo"},
      methods = {"a"})
  void a() {
    this.checkFieldsFoo.a();
  }

  @MustCall("a") static class Foo {
    void a() {}

    void c() {}
  }

  Foo makeFoo() {
    return new Foo();
  }

  @MustCall("a") static class FooField {
    private final @Owning Foo finalOwningFoo;

    public @MustCallAlias FooField(@MustCallAlias Foo f) {
      this.finalOwningFoo = f;
    }

    public Foo ReturnAlias(Foo f) {
      return f;
    }

    @EnsuresCalledMethods(
        value = {"this.finalOwningFoo"},
        methods = {"a"})
    void a() {
      this.finalOwningFoo.a();
      this.finalOwningFoo.c();
    }
  }

  void testField() {
    Foo f = new Foo();
    FooField fooField = new FooField(f);
    fooField.a();
  }
}
