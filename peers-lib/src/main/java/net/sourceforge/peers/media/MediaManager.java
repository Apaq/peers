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
    
 Copyright 2010-2013 Yohann Martineau 
 */
package net.sourceforge.peers.media;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import net.sourceforge.peers.rtp.RtpPacket;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;
import net.sourceforge.peers.sip.core.useragent.UserAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaManager {

    private static final Logger LOG = LoggerFactory.getLogger(MediaManager.class);
    public static final String MEDIA_DIR = "media";
    public static final int DEFAULT_CLOCK = 8000; // Hz
    private UserAgent userAgent;
    private CaptureRtpSender captureRtpSender;
    private IncomingRtpReader incomingRtpReader;
    private RtpSession rtpSession;
    private DtmfFactory dtmfFactory;
    private DatagramSocket datagramSocket;
    
    public MediaManager(UserAgent userAgent) {
        this.userAgent = userAgent;
        dtmfFactory = new DtmfFactory();
    }

    private void startRtpSessionOnSuccessResponse(String localAddress,
            String remoteAddress, int remotePort, Codec codec,
            SoundSource soundSource) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(localAddress);
        } catch (UnknownHostException e) {
            LOG.error("unknown host: " + localAddress, e);
            return;
        }

        rtpSession = new RtpSession(inetAddress, datagramSocket, userAgent.isMediaDebug(), userAgent.getPeersHome());

        try {
            inetAddress = InetAddress.getByName(remoteAddress);
            rtpSession.setRemoteAddress(inetAddress);
        } catch (UnknownHostException e) {
            LOG.error("unknown host: " + remoteAddress, e);
        }
        rtpSession.setRemotePort(remotePort);


        try {
            captureRtpSender = new CaptureRtpSender(rtpSession, soundSource, userAgent.isMediaDebug(), codec, userAgent.getPeersHome());
        } catch (IOException e) {
            LOG.error("input/output error", e);
            return;
        }

        try {
            captureRtpSender.start();
        } catch (IOException e) {
            LOG.error("input/output error", e);
        }
    }

    public void successResponseReceived(String localAddress,
            String remoteAddress, int remotePort, Codec codec) {
        SoundManager soundManager = userAgent.getSoundManager();
        soundManager.open();
        startRtpSessionOnSuccessResponse(localAddress, remoteAddress,
                remotePort, codec, soundManager);

        try {
            incomingRtpReader = new IncomingRtpReader(captureRtpSender.getRtpSession(), soundManager, codec);
        } catch (IOException e) {
            LOG.error("input/output error", e);
            return;
        }

        incomingRtpReader.start();
    }

    private void startRtpSession(String destAddress, int destPort,
            Codec codec, SoundSource soundSource) {
        rtpSession = new RtpSession(userAgent.getConfig().getLocalInetAddress(), datagramSocket, userAgent.isMediaDebug(), 
                userAgent.getPeersHome());

        try {
            InetAddress inetAddress = InetAddress.getByName(destAddress);
            rtpSession.setRemoteAddress(inetAddress);
        } catch (UnknownHostException e) {
            LOG.error("unknown host: " + destAddress, e);
        }
        rtpSession.setRemotePort(destPort);

        try {
            captureRtpSender = new CaptureRtpSender(rtpSession, soundSource, userAgent.isMediaDebug(), codec, userAgent.getPeersHome());
        } catch (IOException e) {
            LOG.error("input/output error", e);
            return;
        }
        try {
            captureRtpSender.start();
        } catch (IOException e) {
            LOG.error("input/output error", e);
        }

    }

    public void handleAck(String destAddress, int destPort, Codec codec) {

        SoundManager soundManager = userAgent.getSoundManager();
        soundManager.open();

        startRtpSession(destAddress, destPort, codec, soundManager);

        try {
            //FIXME RTP sessions can be different !
            incomingRtpReader = new IncomingRtpReader(rtpSession, soundManager, codec);
        } catch (IOException e) {
            LOG.error("input/output error", e);
            return;
        }

        incomingRtpReader.start();

    }

    public void updateRemote(String destAddress, int destPort, Codec codec) {
        try {
            InetAddress inetAddress = InetAddress.getByName(destAddress);
            rtpSession.setRemoteAddress(inetAddress);
        } catch (UnknownHostException e) {
            LOG.error("unknown host: " + destAddress, e);
        }
        rtpSession.setRemotePort(destPort);

    }

    public void sendDtmf(char digit) {
        if (captureRtpSender != null) {
            List<RtpPacket> rtpPackets = dtmfFactory.createDtmfPackets(digit);
            RtpSender rtpSender = captureRtpSender.getRtpSender();
            rtpSender.pushPackets(rtpPackets);
        }
    }

    public void stopSession() {
        if (rtpSession != null) {
            rtpSession.stop();
            while (!rtpSession.isSocketClosed()) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    LOG.debug("sleep interrupted");
                }
            }
            rtpSession = null;
        }
        if (incomingRtpReader != null) {
            incomingRtpReader = null;
        }
        if (captureRtpSender != null) {
            captureRtpSender.stop();
            captureRtpSender = null;
        }
        if (datagramSocket != null) {
            datagramSocket = null;
        }

        SoundManager soundManager = userAgent.getSoundManager();
        if (soundManager != null) {
            soundManager.close();
        }

    }

    public void setDatagramSocket(DatagramSocket datagramSocket) {
        this.datagramSocket = datagramSocket;
    }

    public DatagramSocket getDatagramSocket() {
        return datagramSocket;
    }


}
