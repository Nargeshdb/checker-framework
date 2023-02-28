import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

class EnsuresCalledMethodsTest {
  @InheritableMustCall("h")
  static class Foo {
    void h() {}
  }

  private class ECMWithNullCheck {
    Foo ftest;

    //    void test() {
    //      // :: warning: (required.method.not.called)
    //      ftest = new Foo();
    //    }

    void close() {
      if (ftest != null) {
        ftest.h();
        ftest = null;
      }
    }
  }
}
