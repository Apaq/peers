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
    
    Copyright 2007-2013 Yohann Martineau 
*/

package dk.apaq.peers.sip.core.useragent.handlers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import dk.apaq.peers.Config;
import dk.apaq.peers.sip.RFC3261;
import dk.apaq.peers.sip.core.useragent.InitialRequestManager;
import dk.apaq.peers.sip.core.useragent.RequestManager;
import dk.apaq.peers.sip.core.useragent.SipListener;
import dk.apaq.peers.sip.core.useragent.UserAgent;
import dk.apaq.peers.sip.syntaxencoding.NameAddress;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldValue;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderParamName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaders;
import dk.apaq.peers.sip.syntaxencoding.SipURI;
import dk.apaq.peers.sip.syntaxencoding.SipUriSyntaxException;
import dk.apaq.peers.sip.transaction.ClientTransaction;
import dk.apaq.peers.sip.transaction.ClientTransactionUser;
import dk.apaq.peers.sip.transaction.NonInviteClientTransaction;
import dk.apaq.peers.sip.transaction.Transaction;
import dk.apaq.peers.sip.transaction.TransactionManager;
import dk.apaq.peers.sip.transport.SipRequest;
import dk.apaq.peers.sip.transport.SipResponse;
import dk.apaq.peers.sip.transport.TransportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterHandler extends MethodHandler implements ClientTransactionUser {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterHandler.class);
    public static final int REFRESH_MARGIN = 10; // seconds

    private InitialRequestManager initialRequestManager;

    private Timer timer;

    private String requestUriStr;
    private String profileUriStr;
    private String callIDStr;
    
    //FIXME should be on a profile based context
    private boolean unregisterInvoked;
    private boolean registered;
    
    public RegisterHandler(UserAgent userAgent, TransactionManager transactionManager, TransportManager transportManager) {
        super(userAgent, transactionManager, transportManager);
    }

    //TODO factorize common code here and in invitehandler
    public synchronized ClientTransaction preProcessRegister(SipRequest sipRequest)
            throws SipUriSyntaxException {
        registered = false;
        unregisterInvoked = false;
        SipHeaders sipHeaders = sipRequest.getSipHeaders();
        SipURI destinationUri = RequestManager.getDestinationUri(sipRequest);
        int port = destinationUri.getPort();
        if (port == SipURI.DEFAULT_PORT) {
            port = RFC3261.TRANSPORT_DEFAULT_PORT;
        }
        //TODO if header route is present, addrspec = toproute.nameaddress.addrspec
        String transport = RFC3261.TRANSPORT_UDP;
        Hashtable<String, String> params = destinationUri.getUriParameters();
        if (params != null) {
            String reqUriTransport = params.get(RFC3261.PARAM_TRANSPORT);
            if (reqUriTransport != null) {
                transport = reqUriTransport; 
            }
        }
        SipURI sipUri = userAgent.getConfig().getOutboundProxy();
        if (sipUri == null) {
            sipUri = destinationUri;
        }
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(sipUri.getHost());
        } catch (UnknownHostException e) {
            throw new SipUriSyntaxException("unknown host: "
                    + sipUri.getHost(), e);
        }
        ClientTransaction clientTransaction = transactionManager
            .createClientTransaction(sipRequest, inetAddress, port,
                    transport, null, this);
        //TODO 10.2
        SipHeaderFieldValue to = sipHeaders.get(
                new SipHeaderFieldName(RFC3261.HDR_TO));
        SipHeaderFieldValue from = sipHeaders.get(
                new SipHeaderFieldName(RFC3261.HDR_FROM));
        String fromValue = from.getValue();
        to.setValue(fromValue);
        requestUriStr = destinationUri.toString();
        profileUriStr = NameAddress.nameAddressToUri(fromValue);
        callIDStr = sipHeaders.get(new SipHeaderFieldName(RFC3261.HDR_CALLID))
            .toString();
        return clientTransaction;
    }

    public void unregister() {
        timer.cancel();
        unregisterInvoked = true;
        challenged = false;
    }

    //////////////////////////////////////////////////////////
    // ClientTransactionUser methods
    //////////////////////////////////////////////////////////
    
    public void errResponseReceived(SipResponse sipResponse) {
        String password = userAgent.getConfig().getPassword();
        if (password != null &&  !"".equals(password.trim())) {
            int statusCode = sipResponse.getStatusCode();
            if (statusCode == RFC3261.CODE_401_UNAUTHORIZED
                    || statusCode ==
                        RFC3261.CODE_407_PROXY_AUTHENTICATION_REQUIRED) {
                if (challenged) {
                    notifyListener(sipResponse);
                } else {
                    challenged = true;
                    NonInviteClientTransaction nonInviteClientTransaction =
                        (NonInviteClientTransaction)
                        transactionManager.getClientTransaction(sipResponse);
                    SipRequest sipRequest =
                        nonInviteClientTransaction.getRequest();
                    challengeManager.handleChallenge(sipRequest, sipResponse);
                }
            } else { // not 401 nor 407
                SipHeaders sipHeaders = sipResponse.getSipHeaders();
                SipHeaderFieldName viaName = new SipHeaderFieldName(
                        RFC3261.HDR_VIA);
                SipHeaderFieldValue via = sipHeaders.get(viaName);
                SipHeaderParamName receivedName = new SipHeaderParamName(
                        RFC3261.PARAM_RECEIVED);
                String viaValue = via.getValue();
                int pos = viaValue.indexOf(" ");
                if (pos > -1) {
                    viaValue = viaValue.substring(pos + 1);
                    pos = viaValue.indexOf(RFC3261.TRANSPORT_PORT_SEP);
                    if (pos > -1) {
                        viaValue = viaValue.substring(0, pos);
                    } else {
                        pos = viaValue.indexOf(RFC3261.PARAM_SEPARATOR);
                        if (pos > -1) {
                            viaValue = viaValue.substring(0, pos);
                        }
                    }
                }
                String received = via.getParam(receivedName);
                if (received != null && !"".equals(received.trim())) {
                    if (viaValue.equals(received)) {
                        notifyListener(sipResponse);
                    } else { // received != via ip address
                        try {
                            InetAddress receivedInetAddress =
                                InetAddress.getByName(received);
                            Config config = userAgent.getConfig();
                            config.setPublicInetAddress(receivedInetAddress);
                            userAgent.getUac().register();
                        } catch (UnknownHostException e) {
                            notifyListener(sipResponse);
                            LOG.error(e.getMessage(), e);
                        } catch (SipUriSyntaxException e) {
                            notifyListener(sipResponse);
                            LOG.error(e.getMessage(), e);
                        }
                    }
                } else { // received not provided
                    notifyListener(sipResponse);
                }
            }
        } else { // no password configured 
            notifyListener(sipResponse);
        }
    }

    private void notifyListener(SipResponse sipResponse) {
        SipListener sipListener = userAgent.getSipListener();
        if (sipListener != null) {
            sipListener.registerFailed(sipResponse);
        }
        challenged = false;
    }

    public void provResponseReceived(SipResponse sipResponse,
            Transaction transaction) {
        //meaningless
    }

    public synchronized void successResponseReceived(SipResponse sipResponse,
            Transaction transaction) {
        // 1. retrieve request corresponding to response
        // 2. if request was not an unregister, extract contact and expires,
        //    and start register refresh timer
        // 3. notify sip listener of register success event.
        SipRequest sipRequest = transaction.getRequest();
        SipHeaderFieldName contactName = new SipHeaderFieldName(
                RFC3261.HDR_CONTACT);
        SipHeaderFieldValue requestContact = sipRequest.getSipHeaders()
                .get(contactName);
        SipHeaderParamName expiresParam = new SipHeaderParamName(
                RFC3261.PARAM_EXPIRES);
        String expires = requestContact.getParam(expiresParam);
        challenged = false;
        if (!"0".equals(expires)) {
            // each contact contains an expires parameter giving the expiration
            // in seconds. Thus the binding must be refreshed before it expires.
            SipHeaders sipHeaders = sipResponse.getSipHeaders();
            SipHeaderFieldValue responseContact = sipHeaders.get(contactName);
            if (responseContact == null) {
                return;
            }
            expires = responseContact.getParam(expiresParam);
        	// patch mobicents simple application
            registered = true;
            int delay = -1;
            if (expires == null || "".equals(expires.trim())) {
                delay = 3600;
            }
            if (!unregisterInvoked) {
            	if (delay == -1) {
            		delay = Integer.parseInt(expires) - REFRESH_MARGIN;
            	}
                timer = new Timer(getClass().getSimpleName()
                        + " refresh timer");
                timer.schedule(new RefreshTimerTask(), delay * 1000);
            }
        }
        SipListener sipListener = userAgent.getSipListener();
        if (sipListener != null) {
            sipListener.registerSuccessful(sipResponse);
        }
    }

    public void transactionTimeout(ClientTransaction clientTransaction) {
        SipListener sipListener = userAgent.getSipListener();
        if (sipListener != null) {
            sipListener.registerFailed(null);
        }
    }

    public void transactionTransportError() {
        //TODO alert user
    }

    public boolean isRegistered() {
        return registered;
    }
    
    //////////////////////////////////////////////////////////
    // TimerTask
    //////////////////////////////////////////////////////////

    class RefreshTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                initialRequestManager.createInitialRequest(requestUriStr,
                        RFC3261.METHOD_REGISTER, profileUriStr, callIDStr);
            } catch (SipUriSyntaxException e) {
                LOG.error("syntax error", e);
            }
        }
    }

    public void setInitialRequestManager(InitialRequestManager initialRequestManager) {
        this.initialRequestManager = initialRequestManager;
    }

}
