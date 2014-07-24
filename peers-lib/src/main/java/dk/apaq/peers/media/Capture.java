/*
    This file is part of Peers, a java SIP softphone.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2008, 2009, 2010, 2011 Yohann Martineau 
*/

package dk.apaq.peers.media;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Runnable class which retrieves data from the given SoundSource and writes it to the given PipedOutputStream.
 * It will continue to do so until stopped via the <code>setStopped</code> method.
 */
public class Capture implements Runnable {
    
    private static final Logger LOG = LoggerFactory.getLogger(Capture.class);
    public static final int SAMPLE_SIZE = 16;
    public static final int BUFFER_SIZE = SAMPLE_SIZE * 20;
    
    private PipedOutputStream rawData;
    private boolean isStopped;
    private SoundSource soundSource;
    private CountDownLatch latch;
    
    public Capture(PipedOutputStream rawData, SoundSource soundSource, CountDownLatch latch) {
        this.rawData = rawData;
        this.soundSource = soundSource;
        this.latch = latch;
        isStopped = false;
    }

    public void run() {
        byte[] buffer;
        
        while (!isStopped) {
            buffer = soundSource.readData();
            try {
                if (buffer == null) {
                    break;
                }
                rawData.write(buffer);
                rawData.flush();
            } catch (IOException e) {
                LOG.error("input/output error", e);
                return;
            }
        }
        latch.countDown();
        if (latch.getCount() != 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOG.error("interrupt exception", e);
            }
        }
    }

    public synchronized void setStopped(boolean isStopped) {
        this.isStopped = isStopped;
    }

}
