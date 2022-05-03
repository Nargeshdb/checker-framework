// package inference;
//
// import java.io.Closeable;
// import java.net.Socket;
// import java.io.IOException;
// import java.nio.channels.SocketChannel;
//
// class SimpleServerRpcConnection implements Closeable {
//  final SocketChannel channel;
//  private final Socket socket;
//
//  public SimpleServerRpcConnection(SocketChannel channel,
//      long lastContact) {
//    this.channel = channel;
//    this.socket = channel.socket();
//  }
//
//  @Override
//  public synchronized void close() {
//    if (!channel.isOpen()) return;
//    try {
//      socket.shutdownOutput();
//    } catch (Exception ignored) {
//
//    }
//    if (channel.isOpen()) {
//      try {
//        channel.close();
//      } catch (Exception ignored) {
//      }
//    }
//    try {
//      socket.close();
//    } catch (Exception ignored) {
//    }
//  }
//
// }
