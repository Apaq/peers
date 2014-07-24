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

package dk.apaq.peers.sip.transaction;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

import dk.apaq.peers.sip.RFC3261;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldValue;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderParamName;
import dk.apaq.peers.sip.transport.MessageSender;
import dk.apaq.peers.sip.transport.SipClientTransportUser;
import dk.apaq.peers.sip.transport.SipRequest;
import dk.apaq.peers.sip.transport.SipResponse;
import dk.apaq.peers.sip.transport.TransportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NonInviteClientTransaction extends NonInviteTransaction implements ClientTransaction, SipClientTransportUser {

    private static final Logger LOG = LoggerFactory.getLogger(NonInviteClientTransaction.class);
    public final NonInviteClientTransactionState INIT;
    public final NonInviteClientTransactionState TRYING;
    public final NonInviteClientTransactionState PROCEEDING;
    public final NonInviteClientTransactionState COMPLETED;
    public final NonInviteClientTransactionState TERMINATED;
    
    protected ClientTransactionUser transactionUser;
    protected String transport;
    protected int nbRetrans;
    
    private NonInviteClientTransactionState state;
    //private SipClientTransport sipClientTransport;
    private MessageSender messageSender;
    private int remotePort;
    private InetAddress remoteInetAddress;
    
    NonInviteClientTransaction(String branchId, InetAddress inetAddress, int port, String transport, SipRequest sipRequest, 
            ClientTransactionUser transactionUser, Timer timer, TransportManager transportManager, TransactionManager transactionManager) {
        super(branchId, sipRequest.getMethod(), timer, transportManager, transactionManager);
        
        this.transport = transport;
        
        SipHeaderFieldValue via = new SipHeaderFieldValue("");
        via.addParam(new SipHeaderParamName(RFC3261.PARAM_BRANCH), branchId);
        sipRequest.getSipHeaders().add(new SipHeaderFieldName(RFC3261.HDR_VIA), via, 0);
        
        nbRetrans = 0;
        
        INIT = new NonInviteClientTransactionStateInit(getId(), this);
        state = INIT;
        TRYING = new NonInviteClientTransactionStateTrying(getId(), this);
        PROCEEDING = new NonInviteClientTransactionStateProceeding(getId(), this);
        COMPLETED = new NonInviteClientTransactionStateCompleted(getId(), this);
        TERMINATED = new NonInviteClientTransactionStateTerminated(getId(),  this);
        
        request = sipRequest;
        this.transactionUser = transactionUser;
        
        remotePort = port;
        remoteInetAddress = inetAddress;
        
        try {
            messageSender = transportManager.createClientTransport(
                    request, remoteInetAddress, remotePort, transport);
        } catch (IOException e) {
            LOG.error("input/output error", e);
            transportError();
        }
        //TODO send request
    }
    
    public void setState(NonInviteClientTransactionState state) {
        this.state.log(state);
        this.state = state;
    }

    public void start() {
        state.start();
        
        //17.1.2.2
        
//        try {
//            sipClientTransport = SipTransportFactory.getInstance()
//                    .createClientTransport(this, request, remoteInetAddress,
//                            remotePort, transport);
//            sipClientTransport.send(request);
//        } catch (IOException e) {
//            //e.printStackTrace();
//            transportError();
//        }
        try {
            messageSender.sendMessage(request);
        } catch (IOException e) {
            LOG.error("input/output error", e);
            transportError();
        }
        
        if (RFC3261.TRANSPORT_UDP.equals(transport)) {
            //start timer E with value T1 for retransmission
            timer.schedule(new TimerE(), RFC3261.TIMER_T1);
        }
    
        timer.schedule(new TimerF(), 64 * RFC3261.TIMER_T1);
    }
    
    void sendRetrans(long delay) {
        //sipClientTransport.send(request);
        try {
            messageSender.sendMessage(request);
        } catch (IOException e) {
            LOG.error("input/output error", e);
            transportError();
        }
        timer.schedule(new TimerE(), delay);
    }
    
    public void transportError() {
        state.transportError();
    }
    
    public synchronized void receivedResponse(SipResponse sipResponse) {
        responses.add(sipResponse);
        // 17.1.1
        int statusCode = sipResponse.getStatusCode();
        if (statusCode < RFC3261.CODE_MIN_PROV) {
            LOG.error("invalid response code");
        } else if (statusCode < RFC3261.CODE_MIN_SUCCESS) {
            state.received1xx();
        } else if (statusCode <= RFC3261.CODE_MAX) {
            state.received200To699();
        } else {
            LOG.error("invalid response code");
        }
    }
    
    public void requestTransportError(SipRequest sipRequest, Exception e) {
        // TODO Auto-generated method stub
        
    }

    public void responseTransportError(Exception e) {
        // TODO Auto-generated method stub
        
    }
    
    class TimerE extends TimerTask {
        @Override
        public void run() {
            state.timerEFires();
        }
    }
    
    class TimerF extends TimerTask {
        @Override
        public void run() {
            state.timerFFires();
        }
    }
    
    class TimerK extends TimerTask {
        @Override
        public void run() {
            state.timerKFires();
        }
    }

    public String getContact() {
        if (messageSender != null) {
            return messageSender.getContact();
        }
        return null;
    }
}
