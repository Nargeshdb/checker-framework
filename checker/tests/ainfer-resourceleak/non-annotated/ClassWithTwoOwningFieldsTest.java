import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class ClassWithTwoOwningFieldsTest {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("close")
  private class ClassWithTwoOwningFields {
    // :: warning: (required.method.not.called)
    final @Owning Foo foo1;
    // :: warning: (required.method.not.called)
    final @Owning Foo foo2;

    public ClassWithTwoOwningFields(Foo f1, Foo f2) {
      foo1 = f1;
      foo2 = f2;
    }

    //    @EnsuresCalledMethods(
    //        value = {"this.foo1", "this.foo2"},
    //        methods = {"a"})
    void close() {
      foo1.a();
      foo2.a();
    }
  }
}
