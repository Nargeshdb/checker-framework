import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class CreatesMustCallForTest {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("a")
  static class NonFinalOwningField {
    @Owning Foo f; // expect owning annotation for this field

    // :: warning: (missing.creates.mustcall.for)
    void initialyzeFooWithoutCMCF() {
      f.a();
      f = new Foo();
    }

    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    void a() {
      f.a();
    }
  }

  @InheritableMustCall("a")
  static class InferCreatesMustCallForForCaller {
    @Owning Foo f; // expect owning annotation for this field

    @CreatesMustCallFor("this")
    void initialyzeFooWithoutCMCF2() {
      f.a();
      f = new Foo();
    }

    void callMethodWithCMCF2() {
      // :: warning: (reset.not.owning)
      initialyzeFooWithoutCMCF2();
    }

    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    void a() {
      f.a();
    }
  }

  abstract class SuperClass {

    abstract void overriddenMethod();

    abstract void a();
  }

  @InheritableMustCall("a")
  private class SubClass extends SuperClass {
    @Owning Foo f; // expect owning annotation for this field

    public SubClass() {}

    @Override
    // :: warning: (missing.creates.mustcall.for)
    void overriddenMethod() {
      f.a();
      f = new Foo();
    }

    @Override
    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    void a() {
      f.a();
    }
  }
}
