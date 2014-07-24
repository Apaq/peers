package dk.apaq.peers.media;

/**
 *
 * @author krog
 */
public interface SoundManager extends SoundSource, SoundTarget {

    public void open();
    
    public void close();
    
}
