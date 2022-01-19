// import java.io.IOException;
// import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
// import org.checkerframework.checker.mustcall.qual.MustCall;
// import org.checkerframework.checker.mustcall.qual.MustCallAlias;
//// @MustCall("a")
//
// class CheckFields {
//  private final Foo checkFieldsFoo;
//
//  public CheckFields(Foo f) {
//    this.checkFieldsFoo = f;
//  }
//
//  @EnsuresCalledMethods(
//      value = {"this.checkFieldsFoo"},
//      methods = {"a"})
//  void checkFieldsFinalizer() {
//    this.checkFieldsFoo.a();
//  }
//
//  @MustCall("a") static class Foo {
//    void a() {}
//
//    void c() {}
//  }
//
//  Foo makeFoo() {
//    return new Foo();
//  }
//
//  //  @MustCall("a")
//  static class FooField {
//    private final Foo finalOwningFoo;
//
//    public FooField() {
//      this.finalOwningFoo = new Foo();
//    }
//
//    public @MustCallAlias Foo returnAlias(@MustCallAlias Foo f) {
//      Foo ff = f;
//      return ff;
//    }
//
//    public void owningFoo(Foo f) {
//      Foo ff = returnAlias(f);
//      ff.a();
//    }
//
//    public void notOwningFoo(Foo f) {
//      try {
//        throw new IOException();
//      } catch (IOException e) {
//        f.a();
//      }
//    }
//
//    //    public void owningFooTest() {
//    //      Foo f = new Foo();
//    //      Foo ff = returnAlias(f);
//    //      owningFoo(ff);
//    //    }
//
//    //    public void notOwningFooTest() {
//    //      Foo f = new Foo();
//    //      notOwningFoo(f);
//    //    }
//
//    @EnsuresCalledMethods(
//        value = {"this.finalOwningFoo"},
//        methods = {"a"})
//    void fooFieldFinalizer() {
//      this.finalOwningFoo.a();
//      this.finalOwningFoo.c();
//    }
//  }
//
//  //  void testField() {
//  //    Foo f = new Foo();
//  //    FooField fooField = new FooField(f);
//  //    fooField.a();
//  //  }
// }
