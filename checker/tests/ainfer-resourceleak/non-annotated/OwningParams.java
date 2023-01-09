import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;

class OwningParams {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  private class OwningParamsDirectCall {
    void owningFoo(Foo f) {
      f.a();
    }
  }

  private class OwningParamsIndirectCall {
    @EnsuresCalledMethods(value = { "#1" }, methods = { "a" })
    void hasECM(Foo f) {
      f.a();
    }

    void owningFoo(@Owning Foo f) {
      f.a();
    }

    void owningFooCaller(Foo f1, Foo f2) {
      hasECM(f1);
      owningFoo(f2);
    }
  }
}
