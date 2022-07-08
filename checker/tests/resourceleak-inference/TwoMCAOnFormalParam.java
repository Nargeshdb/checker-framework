// package inference;
//
// import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
// import org.checkerframework.checker.mustcall.qual.MustCall;
// class TwoMCAOnFormalParam extends SimpleChannelInboundHandler<HttpContent> {
//  final Foo foo1;
//  final FooFoo foo2;
//
//  public TwoMCAOnFormalParam(Foo f1, FooFoo f2) {
//    this.foo1 = f1;
//    this.foo2 = f2;
//  }
//
//  void finalizer() {
//    this.foo1.a();
//    this.foo2.a();
//  }
//
//  @MustCall("a") class Foo {
//    void a() {}
//    void c() {}
//  }
//
//  @MustCall("a") class FooFoo {
//    void a() {}
//    void c() {}
//  }
//
// }
//
// abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {}
//
// class ChannelInboundHandlerAdapter{ }
//
// interface HttpContent{ }
