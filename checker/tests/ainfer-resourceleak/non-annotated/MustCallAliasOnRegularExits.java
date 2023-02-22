import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;
import java.io.IOException;

class MustCallAliasOnRegularExits {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}

    int b() throws IOException {
      return 0;
    }
  }

  private abstract class MCAConstructor extends Foo {

    protected final Foo f; // expect owning annotation for this field
    protected long s = 0L;

    // :: warning: (annotations.on.use)
    protected MCAConstructor(Foo foo) throws IOException {
      if (foo == null) {
        this.s = foo.b();
      }
      this.f = foo;
    }

    @EnsuresCalledMethods(
        value = {"this.f"},
        methods = {"a"})
    public void a() {
      f.a();
    }
  }
}
