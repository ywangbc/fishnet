import java.lang.Exception;
import java.lang.Integer;
import java.lang.Math;
import java.lang.System;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @Modified Yu Wang
 * @version 1.0
 *
 * Window ALgorithm: GO BACK N
 */

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        BIND,
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }
    final int PAGESIZE = 4096;
    final int BUFSIZE = PAGESIZE*4;
    final int WINDOWSIZE = PAGESIZE;
    final int SENDTIMEOUT = 1000;

    private State state;
    private int localAddr;
    private int localPort;
    private int remoteAddr;
    private int remotePort;
    private TCPManager tcpMan;
    private Queue<TCPSock> SYNConnections;
    private int backlog;
    ByteBuffer sendBuf; //write buffer, user is responsible for reset the status ready for reading(get) after use
    ByteBuffer recvBuf; //read buffer, user is responsible for reset the status ready for reading(get) after use
    WindowBuffer windowBuffer;
    int flowWinSize; //Window size for flow control, the remaining bytes in recvbuffer, used by sender


    int expectedSendSeq; //Used by receiver, next inorder seq to be ACKed
    int sendBase; //Base of current window
    int nextseqnum; //First Byte in window that is not sent yet


    public TCPSock(TCPManager tcpManager) {
        this.state = State.CLOSED;
        this.tcpMan = tcpManager;
        this.localAddr = tcpManager.getAddr();
        this.localPort = -1;
        this.remoteAddr = -1;
        this.remotePort = -1;
        sendBuf = ByteBuffer.allocate(BUFSIZE);
        recvBuf = ByteBuffer.allocate(BUFSIZE);
        windowBuffer = new WindowBuffer(WINDOWSIZE, tcpMan);
        flowWinSize = 0;
    }

    public void init(State state, SockKey sockKey, int expectedSendSeq) {
        this.state = state;
        this.localAddr = sockKey.localAddr;
        this.localPort = sockKey.localPort;
        this.remoteAddr = sockKey.remoteAddr;
        this.remotePort = sockKey.remotePort;
        this.expectedSendSeq = expectedSendSeq;
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if(this.state !=State.CLOSED) {
            tcpMan.logError("Bind failed, current socket is not closed");
            return -1;
        }
        this.localPort = localPort;
        this.state = State.BIND; //change the state to BIND for LISTEN
        return 0;
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        if(this.state!=State.BIND) {
            tcpMan.logError("Need to bind to a socket before listen");
            return -1;
        }
        this.tcpMan.setListenSock(this);
        this.state = State.LISTEN;
        SYNConnections = new LinkedList<TCPSock>();
        this.backlog = backlog;
        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        if(state != State.LISTEN) {
            tcpMan.logError("Accepting socket is not listening");
            return null;
        }
        if(SYNConnections==null) {
            tcpMan.logError("SYN Connection queue not initialized");
            return null;
        }
        return SYNConnections.poll();
    }

    /**
     * Accept a transport packet
     *  client
     */
    public void onReceive(Transport transPkt, int clientAddr) {
        if(transPkt.getSrcPort() > Transport.MAX_PORT_NUM) {
            tcpMan.logError("srcPort "+transPkt.getSrcPort()+" out of range");
        }
        if(transPkt.getDestPort() > Transport.MAX_PORT_NUM) {
            tcpMan.logError("destPort "+transPkt.getSrcPort()+" out of range");
        }
        int pktType = transPkt.getType();
        if(pktType!=Transport.SYN && pktType!= Transport.ACK && pktType!= Transport.FIN && pktType!=Transport.DATA) {
            tcpMan.logError("transPkt with illegal type: "+transPkt.getType());
            return;
        }

        //If the socket is listening,
        if(isListening()) {
            //data for a client socket, possibly resend data
            //Reply with a fin message to close
            if(pktType==Transport.DATA) {
                //return new Transport(localPort, transPkt.getSrcPort(), Transport.FIN, transPkt.getWindow(), transPkt.getSeqNum(), new byte[0]);
                sendFIN();
            }
            else if(pktType == Transport.FIN) {
                tcpMan.logError("Listening socket received repetitive FIN packet from client, original socket closed, not true error");
            }
            //If listening received ACK from client, the socket is closed but client doesn't know
            //Should not arrive here, client should never send ACK messsage
            else if(pktType == Transport.ACK) {
                tcpMan.logError("Client should not send ACK message to listening server socket");
            }
            //legal type, update local client sockets, send ack message
            else if(pktType == Transport.SYN) {
                if(this.SYNConnections.size() >= backlog) {
                    sendFIN();
                }

                SockKey sockKey = new SockKey(localAddr, localPort, clientAddr, transPkt.getSrcPort());
                //grab a socket from pool
                TCPSock wrSock = tcpMan.socket();
                if(wrSock == null) {
                    tcpMan.logError("Run out of w/r socket for client on server");
                    return;
                }
                //Connection established, update all addresses and ports
                int ackNum = transPkt.getSeqNum()+1;
                wrSock.init(State.ESTABLISHED, sockKey, ackNum);
                tcpMan.getClientSock().put(sockKey, wrSock);
                tcpMan.logOutput("S");
                wrSock.sendACK();
            }
        }
        //If the socket is a r/w client socket
        else {
            switch(pktType) {
                case Transport.SYN:
                    handleSYN(transPkt);
                    break;
                case Transport.ACK:
                    handleACK(transPkt);
                    break;
                case Transport.FIN:
                    handleFIN(transPkt);
                    break;
                case Transport.DATA:
                    handleDATA(transPkt);
                    break;
                default:
                    tcpMan.logError("Should not reach here in TCPSock onReceive");
            }
        }
    }


    //This is not a listening socket, thus a SYN message must be retransmitted due to loss of ACK message
    void handleSYN(Transport transPkt) {
        //previous ACK lost, retransmitted SYN
        if(this.state == State.ESTABLISHED || this.state == State.SHUTDOWN) {
            tcpMan.logOutput("!"); //SYN duplicate at receiver
            sendACK();
        }
        else if(this.state == State.CLOSED){
            tcpMan.logError("Wrong state when receiving retransmitted SYN, state: "+stateToString(this.state)+", should be"+stateToString(State.ESTABLISHED));
        }
        else {
            tcpMan.logError("Un anticipated state in handleSYN: " + stateToString(this.state));
        }
    }

    /**
     *
     * @param transPkt, the packet sent from server to client
     * @param clientAddr
     * @return
     */
    void handleACK(Transport transPkt) {
        //Connection is established and we should update the current state
        if(this.state == State.SYN_SENT) {
            this.state = State.ESTABLISHED;
            sendBase = 0;
            nextseqnum = 0;
            this.flowWinSize = transPkt.getWindow();
            tcpMan.logOutput(":");
            return;
        }

        if(this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            tcpMan.logError("Wrong state in handleACK: "+stateToString(this.state));
            return;
        }

        int ackNum = transPkt.getSeqNum();
        int oldnextseqnum = nextseqnum;
        //repetitive ACK, ignore
        if(ackNum <= sendBase) {
            tcpMan.logOutput("?");
            return;
        }
        //Higer sequence ACK, advance base and clear the windowBuffer, cause we have changed to a new window start index
        //Notice the timer will be started by sendData if sendBase == nextSeqnum
        else {
            tcpMan.logOutput(":");
            this.flowWinSize = transPkt.getWindow();
            int oldSendBase = sendBase;
            sendBase = ackNum;
            windowBuffer.advance(sendBase - oldSendBase);
            sendData();
        }

        //Since we changed base, add a new timer for this new base
        //Notice this timer case has not been handled by sendData
        if(sendBase < oldnextseqnum) {
            try {
                String[] paramTypes = new String[]{"java.lang.Integer"};
                Object[] params = new Object[]{sendBase};
                Method method = Callback.getMethod("resendData", this, paramTypes);
                Callback cb = new Callback(method, (Object) this, params);
                // re-send SYN if timeout
                this.tcpMan.addTimer(SENDTIMEOUT, cb);
            } catch (Exception e) {
                this.tcpMan.logError("Failed to add resend data callback in sendData");
                e.printStackTrace();
            }
        }

        //Three conditions for shut down:
        //1. Shut down command issued
        //2. sendbuf is emptied (all data in buffer sent)
        //3. All ACKs received
        if(this.state == State.SHUTDOWN && this.sendBuf.position()==0
                && this.sendBase==this.nextseqnum) {
            for(int i=0; i<5; i++) {
                sendFIN();
            }
            this.release();
        }
    }
    //By our design we know we must have received all data upon received FIN
    void handleFIN(Transport transPkt) {
        //Duplicate FIN
        if(this.state == State.SHUTDOWN) {
            tcpMan.logOutput("!");
            return;
        }
        tcpMan.logOutput("F");
        this.state = State.SHUTDOWN;
        handleFIN();
    }
    void handleFIN() {
        //We only need one condition to ensure we can close, as sender only send FIN if it has received all ACKs
        // We only need to make sure we read all data from buffer
        if(recvBuf.position()==0) {
            this.release();
            return;
        }

        //Else read has not finished, we schedule another handleFIN in the future
        try {
            Method method = Callback.getMethod("handleFIN", this, null);
            Callback cb = new Callback(method, (Object)this, null);
            // re-send SYN if timeout
            this.tcpMan.addTimer(SENDTIMEOUT, cb);
        }catch (Exception e) {
            this.tcpMan.logError("Failed to add resend handleFIN callback in handleFIN");
            e.printStackTrace();
        }


    }
    void handleDATA(Transport transPkt) {
        if(this.state!=State.ESTABLISHED && this.state!=State.SHUTDOWN) {
            tcpMan.logError("Wrong state in handleDATA: "+stateToString(this.state));
            return;
        }

        int seqNum = transPkt.getSeqNum();
        if(seqNum == this.expectedSendSeq) {
            byte[] payload = transPkt.getPayload();
            if(recvBuf.remaining() < payload.length) {
                tcpMan.logError("Flow control not working, recvBuf overflow");
            }

            Transport synTransPkt = new Transport(localPort, remotePort, Transport.SYN, WINDOWSIZE, -1, new byte[0]);
            this.tcpMan.sendTrans(this.remoteAddr, synTransPkt);
            expectedSendSeq += payload.length;
        }
        sendACK();
    }

    public void sendSYN() {
        //Client just bound to a port
        if(this.state == State.BIND) {
            tcpMan.logOutput("S");
        }
        //Resend SYN
        else if(this.state == State.SYN_SENT) {
            tcpMan.logOutput("!");
        }
        //ACK of SYN received, this must be resend, so abort
        else if(this.state == State.ESTABLISHED) {
            return;
        }
        //We cannot at SHUTDOWN, as FIN is not allowed to send until connection has been established
        else {
            tcpMan.logError("Wrong status in sendSYN: " + stateToString(this.state));
        }

        Transport synTransPkt = new Transport(localPort, remotePort, Transport.SYN, WINDOWSIZE, -1, new byte[0]);
        this.tcpMan.sendTrans(this.remoteAddr, synTransPkt);

        try {
            Method method = Callback.getMethod("sendSYN", this, null);
            Callback cb = new Callback(method, (Object)this, null);
            // re-send SYN if timeout
            this.tcpMan.addTimer(SENDTIMEOUT, cb);
        }catch (Exception e) {
            this.tcpMan.logError("Failed to add resend SYN callback in sendSYN");
            e.printStackTrace();
        }
    }

    public void sendACK() {
        //If the socket is closed, all is done
        if(this.state == State.CLOSED) {
            return;
        }

        //pass in remaining of recvBuf for flow control
        Transport ackTransPkt = new Transport(localPort, remotePort, Transport.ACK, this.recvBuf.remaining(), expectedSendSeq, new byte[0]);
        this.tcpMan.sendTrans(this.remoteAddr, ackTransPkt);

    }

    /*
    public void resendAck(int tracedExpectedSeq) {
        if(this.expectedSendSeq)
    }
    */

    public void sendData() {
        if(this.state != State.ESTABLISHED && this.state != State.SHUTDOWN ) {
            tcpMan.logError("Cannot send data in state: " + stateToString(this.state));
            return;
        }
        if(nextseqnum < sendBase + WINDOWSIZE && nextseqnum < sendBase + this.flowWinSize) {
            int oldnextseqnum = nextseqnum;
            while(nextseqnum < sendBase + WINDOWSIZE && nextseqnum < sendBase + this.flowWinSize) {
                //sendBuf is ready for put, so flip first
                sendBuf.flip();

                int sendSize = Math.min(sendBase + WINDOWSIZE - nextseqnum, sendBuf.remaining()); //Congestion Windows Size, available bytes in sendBuf
                sendSize = Math.min(sendSize, sendBase + this.flowWinSize - nextseqnum); //Flow Window Size
                sendSize = Math.min(sendSize, Transport.MAX_PAYLOAD_SIZE); //Maximum payload size

                //Quick abort if there is nothing to send
                if (sendSize == 0) {
                    return;
                }

                byte[] sendBytes = new byte[sendSize];
                sendBuf.get(sendBytes, 0, sendSize);
                sendBuf.compact();
                windowBuffer.put(sendBytes);

                Transport dataTransPkt = new Transport(localPort, remotePort, Transport.DATA, WINDOWSIZE, nextseqnum, sendBytes);
                this.tcpMan.sendTrans(this.remoteAddr, dataTransPkt);

                nextseqnum += sendSize;
            }
            //The start of a new fresh window, add a timer
            if (sendBase == oldnextseqnum) {
                try {
                    String[] paramTypes = new String[]{"java.lang.Integer"};
                    Object[] params = new Object[]{sendBase};
                    Method method = Callback.getMethod("resendData", this, paramTypes);
                    Callback cb = new Callback(method, (Object) this, params);
                    // re-send SYN if timeout
                    this.tcpMan.addTimer(SENDTIMEOUT, cb);
                } catch (Exception e) {
                    this.tcpMan.logError("Failed to add resend data callback in sendData");
                    e.printStackTrace();
                }
            }
        }
        //Not in window size, do nothing
        else {

        }
    }

    /****
     * resend data from tracing base to nextseqnum
     * @param base: the base traced by timer
     */
    public void resendData(int tracedBase) {
        //If the connection has closed, all data are sent, no need to resend
        if(this.isClosed()) {
            return;
        }
        //If traced bits have been acked, no need to resend
        if(tracedBase < sendBase) {
            return;
        }

        if(tracedBase > sendBase) {
            tcpMan.logError("traced Base is: "+tracedBase + ", but sendBase is: "+sendBase);
            return;
        }
        assert(tracedBase == sendBase);

        byte[] allSendBytes = windowBuffer.get();
        int totalSize = allSendBytes.length;
        int startPos = 0;
        while(startPos < totalSize) {
            int pktSize = Math.min(totalSize-startPos, Transport.MAX_PAYLOAD_SIZE);
            byte[] sendBytes = new byte[pktSize];
            System.arraycopy(allSendBytes, startPos, sendBytes, 0, pktSize);
            Transport dataTransPkt = new Transport(localPort, remotePort, Transport.DATA, WINDOWSIZE, tracedBase, sendBytes);
            this.tcpMan.sendTrans(this.remoteAddr, dataTransPkt);

            startPos += pktSize;
        }

        try {
            String[] paramTypes = new String[]{"java.lang.Integer"};
            Object[] params = new Object[]{tracedBase};
            Method method = Callback.getMethod("resendData", this, paramTypes);
            Callback cb = new Callback(method, (Object) this, params);
            // re-send SYN if timeout
            this.tcpMan.addTimer(SENDTIMEOUT, cb);
        } catch (Exception e) {
            this.tcpMan.logError("Failed to add resend data callback in resendData");
            e.printStackTrace();
        }
    }


    public void sendFIN() {
        if(this.state != State.SHUTDOWN && this.state != State.ESTABLISHED) {
            tcpMan.logError("Cannot send FIN before connection is established");
            return;
        }
        tcpMan.logOutput("F");
        Transport finTransPkt = new Transport(this.localPort, this.remotePort, Transport.FIN, 0, 0, new byte[0]);
        this.tcpMan.sendTrans(this.remoteAddr, finTransPkt);
    }


    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    public boolean isListening() {
        return (state == State.LISTEN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        if(this.state != State.BIND) {
            tcpMan.logError("Connecting with an unbind client socket, with status" + stateToString(this.state));
            return -1;
        }
        this.remoteAddr = destAddr;
        this.remotePort = destPort;
        SockKey sockKey = new SockKey(this.localAddr, this.localPort, this.remoteAddr, this.remotePort);
        this.tcpMan.getClientSock().put(sockKey, this);
        sendSYN();
        this.state = State.SYN_SENT;
        return 0;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        //Send a lot of FIN in case of message lost
        //This is not elegant but we cannot change the protocol
        this.state = State.SHUTDOWN;
        //Only send FIN when all data sent and ACKs received
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        if(SYNConnections != null) {
            while (!SYNConnections.isEmpty()) {
                TCPSock sock = SYNConnections.poll();
                sock.release();
            }
        }
        this.state = State.CLOSED;
        sendBuf.clear();
        recvBuf.clear();
        windowBuffer.clear();
        this.localPort = -1;
        this.remoteAddr = -1;
        this.remotePort = -1;
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        if(this.state != State.ESTABLISHED) {
            tcpMan.logError("Cannot write, connection not established");
        }
        int writeCount = Math.min(sendBuf.remaining(), len);
        sendBuf.put(buf, pos, writeCount);
        sendData();
        return writeCount;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        //We cannot read from a socket that is already closed
        //A connection must have sent everything before it has send FIN
        if(this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            return -1;
        }
        recvBuf.flip();
        int readCount = Math.min(len, recvBuf.remaining());
        recvBuf.get(buf, pos, readCount);
        recvBuf.compact();
        return readCount;
    }

    /*
     * End of socket API
     */
    String stateToString(State curState) {
        switch(curState) {
            case BIND:
                return "BIND";
            case CLOSED:
                return "CLOSED";
            case ESTABLISHED:
                return "ESTABLISHED";
            case LISTEN:
                return "LISTEN";
            case SHUTDOWN:
                return "SHUTDOWN";
            case SYN_SENT:
                return "SYN_SEND";
            default:
                return ("UNKNOWN STATE: "+ curState);
        }
    }
}
