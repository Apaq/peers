package net.sourceforge.peers.media;

/**
 *
 * @author krog
 */
public class SineSoundManager implements SoundManager {

    private final byte[] table;

    public SineSoundManager() {
        //Generate table
        
        table = new byte[8000];
        for(int i=0;i<table.length;i++) {
            table[i] = (byte)(Byte.MAX_VALUE * Math.sin(2*Math.PI * i));
        }
    }
    
    public void open() { }

    public void close() { }

    public byte[] readData() {
        return table;
    }

    public int writeData(byte[] buffer, int offset, int length) {
        // NOOP
        return length;
    }
    
}
