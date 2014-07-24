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

package dk.apaq.peers.sip.core.useragent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

import dk.apaq.peers.sip.RFC3261;
import dk.apaq.peers.sip.core.useragent.handlers.ByeHandler;
import dk.apaq.peers.sip.core.useragent.handlers.CancelHandler;
import dk.apaq.peers.sip.core.useragent.handlers.InviteHandler;
import dk.apaq.peers.sip.core.useragent.handlers.OptionsHandler;
import dk.apaq.peers.sip.core.useragent.handlers.RegisterHandler;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldValue;
import dk.apaq.peers.sip.syntaxencoding.SipHeaders;
import dk.apaq.peers.sip.syntaxencoding.SipURI;
import dk.apaq.peers.sip.transaction.ClientTransaction;
import dk.apaq.peers.sip.transaction.ClientTransactionUser;
import dk.apaq.peers.sip.transaction.ServerTransaction;
import dk.apaq.peers.sip.transaction.ServerTransactionUser;
import dk.apaq.peers.sip.transaction.Transaction;
import dk.apaq.peers.sip.transaction.TransactionManager;
import dk.apaq.peers.sip.transactionuser.Dialog;
import dk.apaq.peers.sip.transactionuser.DialogManager;
import dk.apaq.peers.sip.transport.SipRequest;
import dk.apaq.peers.sip.transport.SipResponse;
import dk.apaq.peers.sip.transport.TransportManager;


public class MidDialogRequestManager extends RequestManager implements ClientTransactionUser, ServerTransactionUser {

    public MidDialogRequestManager(UserAgent userAgent, InviteHandler inviteHandler, CancelHandler cancelHandler, ByeHandler byeHandler,
            OptionsHandler optionsHandler, RegisterHandler registerHandler, DialogManager dialogManager, TransactionManager transactionManager,
            TransportManager transportManager) {
        super(userAgent, inviteHandler, cancelHandler, byeHandler, optionsHandler, registerHandler, dialogManager, transactionManager,
                transportManager);
    }


    ////////////////////////////////////////////////
    // methods for UAC
    ////////////////////////////////////////////////

    public void generateMidDialogRequest(Dialog dialog,
            String method, MessageInterceptor messageInterceptor) {
        

        SipRequest sipRequest = dialog.buildSubsequentRequest(RFC3261.METHOD_BYE);

        if (RFC3261.METHOD_BYE.equals(method)) {
            byeHandler.preprocessBye(sipRequest, dialog);
        }
        //TODO check that subsequent request is supported before client
        //transaction creation
        if (!RFC3261.METHOD_INVITE.equals(method)) {
            ClientTransaction clientTransaction = createNonInviteClientTransaction(
            		sipRequest, null, byeHandler);
            if (messageInterceptor != null) {
                messageInterceptor.postProcess(sipRequest);
            }
            if (clientTransaction != null) {
                clientTransaction.start();
            }
        } else {
            //TODO client transaction user is managed by invite handler directly
        }

        
    }
    
    
    public ClientTransaction createNonInviteClientTransaction(
            SipRequest sipRequest, String branchId,
            ClientTransactionUser clientTransactionUser) {
        //8.1.2
        SipURI destinationUri = RequestManager.getDestinationUri(sipRequest);

        //TODO if header route is present, addrspec = toproute.nameaddress.addrspec
        String transport = RFC3261.TRANSPORT_UDP;
        Hashtable<String, String> params = destinationUri.getUriParameters();
        if (params != null) {
            String reqUriTransport = params.get(RFC3261.PARAM_TRANSPORT);
            if (reqUriTransport != null) {
                transport = reqUriTransport; 
            }
        }
        int port = destinationUri.getPort();
        if (port == SipURI.DEFAULT_PORT) {
            port = RFC3261.TRANSPORT_DEFAULT_PORT;
        }
        SipURI sipUri = userAgent.getConfig().getOutboundProxy();
        if (sipUri == null) {
            sipUri = destinationUri;
        }
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(sipUri.getHost());
        } catch (UnknownHostException e) {
            logger.error("unknown host: " + sipUri.getHost(), e);
            return null;
        }
        ClientTransaction clientTransaction = transactionManager
            .createClientTransaction(sipRequest, inetAddress, port, transport,
                    branchId, clientTransactionUser);
        return clientTransaction;
    }
    
    
    
    
    

    
    
    
    
    
    ////////////////////////////////////////////////
    // methods for UAS
    ////////////////////////////////////////////////

    public void manageMidDialogRequest(SipRequest sipRequest, Dialog dialog) {
        SipHeaders sipHeaders = sipRequest.getSipHeaders();
        SipHeaderFieldValue cseq =
            sipHeaders.get(new SipHeaderFieldName(RFC3261.HDR_CSEQ));
        String cseqStr = cseq.getValue();
        int pos = cseqStr.indexOf(' ');
        if (pos < 0) {
            pos = cseqStr.indexOf('\t');
        }
        int newCseq = Integer.parseInt(cseqStr.substring(0, pos));
        int oldCseq = dialog.getRemoteCSeq();
        if (oldCseq == Dialog.EMPTY_CSEQ) {
            dialog.setRemoteCSeq(newCseq);
        } else if (newCseq < oldCseq) {
            // out of order
            // RFC3261 12.2.2 p77
            // TODO test out of order in-dialog-requests
            SipResponse sipResponse = generateResponse(sipRequest, dialog,
                    RFC3261.CODE_500_SERVER_INTERNAL_ERROR,
                    RFC3261.REASON_500_SERVER_INTERNAL_ERROR);
            ServerTransaction serverTransaction =
                transactionManager.createServerTransaction(
                        sipResponse,
                        userAgent.getSipPort(),
                        RFC3261.TRANSPORT_UDP,
                        this, sipRequest);
            serverTransaction.start();
            serverTransaction.receivedRequest(sipRequest);
            serverTransaction.sendReponse(sipResponse);
        } else {
            dialog.setRemoteCSeq(newCseq);
        }

        String method = sipRequest.getMethod();
        if (RFC3261.METHOD_BYE.equals(method)) {
            byeHandler.handleBye(sipRequest, dialog);
        } else if (RFC3261.METHOD_INVITE.equals(method)) {
            inviteHandler.handleReInvite(sipRequest, dialog);
        } else if (RFC3261.METHOD_ACK.equals(method)) {
            inviteHandler.handleAck(sipRequest, dialog);
        } else if (RFC3261.METHOD_OPTIONS.equals(method)) {
            optionsHandler.handleOptions(sipRequest);
        }
    }

    ///////////////////////////////////////
    // ServerTransactionUser methods
    ///////////////////////////////////////
    @Override
    public void transactionFailure() {
        // TODO Auto-generated method stub
        
    }


    ///////////////////////////////////////
    // ClientTransactionUser methods
    ///////////////////////////////////////
    // callbacks employed for cancel responses (ignored)
	@Override
	public void transactionTimeout(ClientTransaction clientTransaction) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void provResponseReceived(SipResponse sipResponse,
			Transaction transaction) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void errResponseReceived(SipResponse sipResponse) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void successResponseReceived(SipResponse sipResponse,
			Transaction transaction) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void transactionTransportError() {
		// TODO Auto-generated method stub
		
	}
}
