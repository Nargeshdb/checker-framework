// import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
// import org.checkerframework.checker.mustcall.qual.MustCall;
//// @MustCall("a")
// class CheckFields {
//  final Foo checkFieldsFoo;
//
//  public CheckFields(Foo f) {
//    this.checkFieldsFoo = f;
//  }
//
//  public Foo returnFoo(Foo f) {
//    return f;
//  }
//
//  public Foo mcaTest(Foo f) {
//    Foo f1 = returnFoo(f);
//    return f1;
//  }
//
//  @EnsuresCalledMethods(
//      value = {"this.checkFieldsFoo"},
//      methods = {"a"})
//  void checkFieldsFinalizer() {
//    this.checkFieldsFoo.a();
//  }
//
//  @MustCall("a") class Foo {
//    void a() {}
//
//    void c() {}
//  }
//
//  Foo makeFoo() {
//    return new Foo();
//  }
// }
