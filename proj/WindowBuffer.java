import java.lang.System;

/************
 * Store the data in the tcp window
 * Since sendBuf and receiveBuf implemented as ByteBuffer cannot
 *
 */

public class WindowBuffer {
    private byte[] buf;
    private int index; //next byte would be put here
    private TCPManager tcpMan;
    WindowBuffer(int winSize, TCPManager tcpMan) {
        buf = new byte[winSize];
        index = 0;
        this.tcpMan = tcpMan;
    }
    public int put(byte[] srcBuf) {
        if(index + srcBuf.length > buf.length) {
            tcpMan.logError("srcBuf is too big for WindowBuffer, it has size: " + srcBuf.length);
            tcpMan.logOutput("srcBuf is too big for WindowBuffer, it has size: " + srcBuf.length);
            return -1;
        }
        System.arraycopy(srcBuf, 0, this.buf, index, srcBuf.length);
        index += srcBuf.length;
        return 0;
    }
    public byte[] get() {
        byte[] retBuf = new byte[index];
        System.arraycopy(buf, 0, retBuf, 0, index);
        return retBuf;
    }

    public void advance(int numBytes) {
        if(index < numBytes) {
            tcpMan.logError("No enough bytes in WindowBuffer, index is smaller than numBytes");
            tcpMan.logOutput("No enough bytes in WindowBuffer, index is smaller than numBytes");
            return;
        }
        System.arraycopy(this.buf, numBytes, this.buf, 0, index-numBytes);
        index -= numBytes;
    }

    public void clear() {
        buf = null;
        index = 0;
        tcpMan = null;
    }
}