package dk.apaq.peers.media;

/**
 *
 * @author krog
 */
public interface SoundTarget {
 
    /**
     * write raw data linear PCM 8kHz, 16 bits signed, mono-channel, little endian
     * @return
     */
    int writeData(byte[] buffer, int offset, int length);
}
