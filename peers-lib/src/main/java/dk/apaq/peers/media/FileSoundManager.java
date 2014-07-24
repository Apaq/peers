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
    
    Copyright 2012 Yohann Martineau 
*/
package dk.apaq.peers.media;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import dk.apaq.peers.sip.core.useragent.handlers.RegisterHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// To create an audio file for peers, you can use audacity:
//
// Edit > Preferences
//
// - Peripherals
//   - Channels: 1 (Mono)
// - Quality
//   - Sampling frequency of default: 8000 Hz
//   - Default Sample Format: 16 bits
//
// Validate
//
// Record audio
//
// File > Export
//
// - File name: test.raw
//
// Validate

public class FileSoundManager implements SoundManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileSoundManager.class);
    public final static int BUFFER_SIZE = 256;

    private FileInputStream fileInputStream;
    
    public FileSoundManager(String fileName) {
        try {
            fileInputStream = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            LOG.error("file not found: " + fileName, e);
        }
    }

    public synchronized void close() {
        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException e) {
                LOG.error("io exception", e);
            }
            fileInputStream = null;
        }
    }

    @Override
    public synchronized byte[] readData() {
        if (fileInputStream == null) {
            return null;
        }
        byte buffer[] = new byte[BUFFER_SIZE];
        try {
            if (fileInputStream.read(buffer) >= 0) {
                LOG.debug("buffer read from file");
                Thread.sleep(15);
                return buffer;
            } else {
                LOG.debug("file has no more data.");
                
                fileInputStream.close();
                fileInputStream = null;
            }
        } catch (IOException e) {
            LOG.error("io exception", e);
        } catch (InterruptedException e) {
            LOG.debug("file reader interrupted");
        }
        return null;
    }

    public void open() {
        
    }

    public int writeData(byte[] buffer, int offset, int length) {
        return length;
    }

}
