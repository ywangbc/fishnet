import java.lang.System;

public class CongestionControl {
    static final int MAXWINSIZE = 4096;
    public int windowSize;
    double doubleWinSize;
    int ssthresh = 2048;
    int duplicate = 1;

    public CongestionControl() {
        windowSize = 1;
        doubleWinSize = 1;
        System.err.println(windowSize+" ");
    }

    public void increment() {
        if(windowSize < ssthresh) {
            slowStart();
        }
        else {
            conAvoid();
        }
        System.err.println(windowSize+" ");
    }

    public void slowStart() {
        windowSize += 1;
        doubleWinSize += 1;
        limit();
    }

    public void conAvoid() {
        doubleWinSize += 1.0/doubleWinSize;
        windowSize = (int) doubleWinSize;
        limit();
    }

    public void tripleAck() {
        doubleWinSize = doubleWinSize/2.0;
        ssthresh = (int)doubleWinSize;
        windowSize = ssthresh;
        System.err.println(windowSize+" ");
    }

    public void timeout() {
        ssthresh = (int)(doubleWinSize/2);
        windowSize = 1;
        doubleWinSize = 1.0;
        System.err.println(windowSize+" ");
    }

    public void duplicate() {
        duplicate++;
        if(duplicate >= 3) {
            this.tripleAck();
        }
    }

    public void newAck() {
        duplicate = 1;
    }

    public void limit() {
        if(windowSize > MAXWINSIZE) {
            windowSize = MAXWINSIZE;
            doubleWinSize = MAXWINSIZE;
        }
    }


}