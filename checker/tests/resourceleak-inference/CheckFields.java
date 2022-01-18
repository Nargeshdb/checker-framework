import java.io.IOException;
import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
import org.checkerframework.checker.mustcall.qual.MustCall;
import org.checkerframework.checker.mustcall.qual.MustCallAlias;
import org.checkerframework.checker.mustcall.qual.Owning;

@MustCall("a") class CheckFields {
  private final @Owning Foo checkFieldsFoo;

  public CheckFields(Foo f) {
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

    public FooField() {
      this.finalOwningFoo = new Foo();
    }

    public @MustCallAlias Foo returnAlias(@MustCallAlias Foo f) {
      Foo ff = f;
      return ff;
    }

    public void owningFoo(Foo f) {
      Foo ff = returnAlias(f);
      ff.a();
    }

    public void notOwningFoo(Foo f) {
      try {
        throw new IOException();
      } catch (IOException e) {
        f.a();
      }
    }

    //    public void owningFooTest() {
    //      Foo f = new Foo();
    //      Foo ff = returnAlias(f);
    //      owningFoo(ff);
    //    }

    //    public void notOwningFooTest() {
    //      Foo f = new Foo();
    //      notOwningFoo(f);
    //    }

    @EnsuresCalledMethods(
        value = {"this.finalOwningFoo"},
        methods = {"a"})
    void a() {
      this.finalOwningFoo.a();
      this.finalOwningFoo.c();
    }
  }

  //  void testField() {
  //    Foo f = new Foo();
  //    FooField fooField = new FooField(f);
  //    fooField.a();
  //  }
}
