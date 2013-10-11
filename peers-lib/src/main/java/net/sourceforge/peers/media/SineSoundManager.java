package net.sourceforge.peers.media;

import java.util.Arrays;

/**
 *
 * @author krog
 */
public class SineSoundManager implements SoundManager {

    
    private final byte[] table;
    int index=0;

    public SineSoundManager() {
        //Generate table
        
        table = new byte[8192];
        for(int i=0;i<table.length;i++) {
            table[i] = (byte)(Byte.MAX_VALUE * Math.sin(2*Math.PI * i));
        }
    }
    
    public void open() { }

    public void close() { }

    public byte[] readData() {
        byte[] buffer = Arrays.copyOfRange(table, index * 256, (index+1) * 256);
        index++;
        if(index==32) {
            index = 0;
        }
        try {
            Thread.sleep(15);
        } catch(Exception ex) {
            
        }
        return buffer;
    }

    public int writeData(byte[] buffer, int offset, int length) {
        // NOOP
        return length;
    }
    
}
