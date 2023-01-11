import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;

class OwningParams {
  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("dispose")
  private class MCAConstructor {

    final @Owning Foo f; // expect owning annotation for this field

    @MustCallAlias MCAConstructor(@MustCallAlias Foo foo) {
      f = foo;
    }

    @EnsuresCalledMethods(value = { "this.f" }, methods = { "a" })
    public void dispose() {
      f.a();
    }
  }

  void testMCAConstructor() {
    Foo f = new Foo();
    MCAConstructor mcac = new MCAConstructor(f);
    mcac.dispose();
  }
}
