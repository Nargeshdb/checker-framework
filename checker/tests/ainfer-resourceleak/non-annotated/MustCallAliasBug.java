import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;
import java.nio.channels.SocketChannel;
import java.net.Socket;
import java.io.IOException;

class MustCallAliasBug {

  @InheritableMustCall("a")
  static class Foo {
    void a() {}
  }

  @InheritableMustCall("a")
  private class MCASuperClass {
    int i;

    MCASuperClass(int i) {
      i = i;
    }

    public void a() {
    }
  }

  private class MCASuperCallBug extends MCASuperClass {

    final @Owning SocketChannel channel;
    private final @Owning Socket socket;

    MCASuperCallBug(int i, SocketChannel channel) {
      super(i);
      this.channel = channel;
      this.socket = channel.socket();
      try {
        socket.setSendBufferSize(2);
      } catch (IOException e) {

      }
    }

    @org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods(value = { "this.channel" }, methods = { "close"})
    public void a() {
      try {
        channel.close();
      } catch (Exception ignored) {
      }
    }
  }

}
