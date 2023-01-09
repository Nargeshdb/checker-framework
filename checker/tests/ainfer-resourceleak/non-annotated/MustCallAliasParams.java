import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.calledmethods.qual.*;
class MustCallAliasParams {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("dispose")
  private class MCAConstructor {

    // :: warning: (required.method.not.called)
    final @Owning Foo f; // expect owning annotation for this field

    MCAConstructor(Foo foo) {
      f = foo;
    }

    public void dispose() {
      f.a();
    }
  }

  void testMCAConstructor() {
    // :: warning: (required.method.not.called)
    Foo f = new Foo();
    MCAConstructor mcac = new MCAConstructor(f);
    mcac.dispose();
  }

  @InheritableMustCall("dispose")
  private class MCASuperClass {
    final @Owning Foo f;
    @MustCallAlias MCASuperClass(@MustCallAlias Foo foo) {
      f = foo;
    }

    @EnsuresCalledMethods(value = { "this.f" }, methods = { "a" })
    public void dispose() {
      f.a();
    }
  }

  private class MCASuperCall extends MCASuperClass {
    MCASuperCall(Foo foo) {
      super(foo);
    }
  }

  private class MCAThisCall extends MCASuperClass {
    @MustCallAlias MCAThisCall(@MustCallAlias Foo foo) {
      super(foo);
    }
    MCAThisCall(Foo foo, boolean b) {
      this(foo);
    }
  }

  private class MCAMethod {
    Foo returnFoo(Foo foo) {
      return foo;
    }

    void returnFooTest() {
      // :: warning: (required.method.not.called)
      Foo f = new Foo();
      MCAMethod mcam = new MCAMethod();
      Foo foo = returnFoo(f);
      foo.a();
    }

    Foo returnAliasFoo(Foo foo) {
      Foo f = foo;
      return f;
    }

    void returnAliasFooTest() {
      // :: warning: (required.method.not.called)
      Foo f = new Foo();
      MCAMethod mcam = new MCAMethod();
      Foo foo = returnAliasFoo(f);
      foo.a();
    }

    Foo returnFooAllPaths(Foo foo) {
      if (true) {
        Foo f = foo;
        return f;
      } else {
        return foo;
      }
    }

    void returnFooAllPathsTest() {
      // :: warning: (required.method.not.called)
      Foo f = new Foo();
      MCAMethod mcam = new MCAMethod();
      Foo foo = returnFooAllPaths(f);
      foo.a();
    }

    // We expect @MustCallAlias for foo even if it's not returned on all paths
    Foo returnFooSomePath(Foo foo) {
      if (true) {
        Foo f = new Foo();
        return f;
      } else {
        return foo;
      }
    }

    void returnFooSomePathTest() {
      // :: warning: (required.method.not.called)
      Foo f = new Foo();
      MCAMethod mcam = new MCAMethod();
      Foo foo = returnFooSomePath(f);
      foo.a();
    }
  }
}
