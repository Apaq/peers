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
    
    Copyright 2007, 2008, 2009, 2010 Yohann Martineau 
*/

package net.sourceforge.peers.sip.core.useragent;

import java.io.File;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.peers.Config;
import net.sourceforge.peers.XmlConfig;
import net.sourceforge.peers.media.Echo;
import net.sourceforge.peers.media.MediaManager;
import net.sourceforge.peers.media.DefaultSoundManager;
import net.sourceforge.peers.media.SoundManager;
import net.sourceforge.peers.sdp.SDPManager;
import net.sourceforge.peers.sip.Utils;
import net.sourceforge.peers.sip.core.useragent.handlers.ByeHandler;
import net.sourceforge.peers.sip.core.useragent.handlers.CancelHandler;
import net.sourceforge.peers.sip.core.useragent.handlers.InviteHandler;
import net.sourceforge.peers.sip.core.useragent.handlers.OptionsHandler;
import net.sourceforge.peers.sip.core.useragent.handlers.RegisterHandler;
import net.sourceforge.peers.sip.syntaxencoding.SipURI;
import net.sourceforge.peers.sip.transaction.Transaction;
import net.sourceforge.peers.sip.transaction.TransactionManager;
import net.sourceforge.peers.sip.transactionuser.DialogManager;
import net.sourceforge.peers.sip.transport.SipMessage;
import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;
import net.sourceforge.peers.sip.transport.TransportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserAgent {

    private static final Logger LOG = LoggerFactory.getLogger(UserAgent.class);
    public final static String CONFIG_FILE = "conf" + File.separator + "peers.xml";
    public final static int RTP_DEFAULT_PORT = 8000;

    private String peersHome;
    private Config config;

    private List<String> peers;
    //private List<Dialog> dialogs;
    
    //TODO factorize echo and captureRtpSender
    private Echo echo;
    
    private UAC uac;
    private UAS uas;

    private ChallengeManager challengeManager;
    
    private DialogManager dialogManager;
    private TransactionManager transactionManager;
    private TransportManager transportManager;

    private int cseqCounter;
    private SipListener sipListener;
    
    private SDPManager sdpManager;
    private SoundManager soundManager;
    private MediaManager mediaManager;

    public UserAgent(SipListener sipListener, String peersHome) throws SocketException {
        this(sipListener, null, peersHome);
    }

    public UserAgent(SipListener sipListener, Config config) throws SocketException {
        this(sipListener, config, null, null);
    }
    
    public UserAgent(SipListener sipListener, Config config, SoundManager soundManager) throws SocketException {
        this(sipListener, config, null, soundManager);
    }
    
    private UserAgent(SipListener sipListener, Config config, String peersHome) throws SocketException {
        this(sipListener, config, peersHome, null);
    }

    private UserAgent(SipListener sipListener, Config config, String peersHome, SoundManager soundManager) throws SocketException {
        this.sipListener = sipListener;
        if (peersHome == null) {
            peersHome = Utils.DEFAULT_PEERS_HOME;
        }
        this.peersHome = peersHome;
        if (config == null) {
            config = new XmlConfig(this.peersHome + File.separator + CONFIG_FILE);
        }
        this.config = config;

        cseqCounter = 1;
        
        StringBuffer buf = new StringBuffer();
        buf.append("starting user agent [");
        buf.append("myAddress: ");
        buf.append(config.getLocalInetAddress().getHostAddress()).append(", ");
        buf.append("sipPort: ");
        buf.append(config.getSipPort()).append(", ");
        buf.append("userpart: ");
        buf.append(config.getUserPart()).append(", ");
        buf.append("domain: ");
        buf.append(config.getDomain()).append("]");
        LOG.info(buf.toString());

        //transaction user
        
        dialogManager = new DialogManager();
        
        //transaction
        
        transactionManager = new TransactionManager();
        
        //transport
        
        transportManager = new TransportManager(transactionManager, config);
        
        transactionManager.setTransportManager(transportManager);
        
        //core
        
        InviteHandler inviteHandler = new InviteHandler(this, dialogManager, transactionManager, transportManager);
        CancelHandler cancelHandler = new CancelHandler(this, dialogManager, transactionManager, transportManager);
        ByeHandler byeHandler = new ByeHandler(this, dialogManager, transactionManager, transportManager);
        OptionsHandler optionsHandler = new OptionsHandler(this, transactionManager, transportManager);
        RegisterHandler registerHandler = new RegisterHandler(this, transactionManager, transportManager);
        
        InitialRequestManager initialRequestManager = new InitialRequestManager(this, inviteHandler, cancelHandler, byeHandler, optionsHandler,
                registerHandler, dialogManager, transactionManager, transportManager);
        MidDialogRequestManager midDialogRequestManager = new MidDialogRequestManager(this, inviteHandler, cancelHandler, byeHandler,
                optionsHandler, registerHandler, dialogManager, transactionManager, transportManager);
        
        uas = new UAS(this, initialRequestManager, midDialogRequestManager, dialogManager, transactionManager, transportManager);

        uac = new UAC(this, initialRequestManager, midDialogRequestManager, dialogManager, transactionManager, transportManager);

        challengeManager = new ChallengeManager(config, initialRequestManager, midDialogRequestManager, dialogManager);
        registerHandler.setChallengeManager(challengeManager);
        inviteHandler.setChallengeManager(challengeManager);
        byeHandler.setChallengeManager(challengeManager);

        peers = new ArrayList<String>();
        //dialogs = new ArrayList<Dialog>();

        sdpManager = new SDPManager(this);
        inviteHandler.setSdpManager(sdpManager);
        optionsHandler.setSdpManager(sdpManager);
        this.soundManager = soundManager == null ? new DefaultSoundManager(config.isMediaDebug(), peersHome) : soundManager;
        this.mediaManager = new MediaManager(this);
    }

    public void close() {
        transportManager.closeTransports();
        config.setPublicInetAddress(null);
    }

    /**
     * Gives the sipMessage if sipMessage is a SipRequest or 
     * the SipRequest corresponding to the SipResponse
     * if sipMessage is a SipResponse
     * @param sipMessage
     * @return null if sipMessage is neither a SipRequest neither a SipResponse
     */
    public SipRequest getSipRequest(SipMessage sipMessage) {
        if (sipMessage instanceof SipRequest) {
            return (SipRequest) sipMessage;
        } else if (sipMessage instanceof SipResponse) {
            SipResponse sipResponse = (SipResponse) sipMessage;
            Transaction transaction = (Transaction)transactionManager
                .getClientTransaction(sipResponse);
            if (transaction == null) {
                transaction = (Transaction)transactionManager
                    .getServerTransaction(sipResponse);
            }
            if (transaction == null) {
                return null;
            }
            return transaction.getRequest();
        } else {
            return null;
        }
    }
    
//    public List<Dialog> getDialogs() {
//        return dialogs;
//    }

    public List<String> getPeers() {
        return peers;
    }

//    public Dialog getDialog(String peer) {
//        for (Dialog dialog : dialogs) {
//            String remoteUri = dialog.getRemoteUri();
//            if (remoteUri != null) {
//                if (remoteUri.contains(peer)) {
//                    return dialog;
//                }
//            }
//        }
//        return null;
//    }

    public String generateCSeq(String method) {
        StringBuffer buf = new StringBuffer();
        buf.append(cseqCounter++);
        buf.append(' ');
        buf.append(method);
        return buf.toString();
    }
    
    public boolean isRegistered() {
        return uac.getInitialRequestManager().getRegisterHandler()
            .isRegistered();
    }

    public UAS getUas() {
        return uas;
    }

    public UAC getUac() {
        return uac;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }
    
    public int getSipPort() {
        return transportManager.getSipPort();
    }

    public int getRtpPort() {
        return config.getRtpPort();
    }

    public String getDomain() {
        return config.getDomain();
    }

    public String getUserpart() {
        return config.getUserPart();
    }

    public boolean isMediaDebug() {
        return config.isMediaDebug();
    }

    public SipURI getOutboundProxy() {
        return config.getOutboundProxy();
    }

    public Echo getEcho() {
        return echo;
    }

    public void setEcho(Echo echo) {
        this.echo = echo;
    }

    public SipListener getSipListener() {
        return sipListener;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public MediaManager getMediaManager() {
        return mediaManager;
    }

    public Config getConfig() {
        return config;
    }

    public String getPeersHome() {
        return peersHome;
    }

    public TransportManager getTransportManager() {
        return transportManager;
    }

    
}
