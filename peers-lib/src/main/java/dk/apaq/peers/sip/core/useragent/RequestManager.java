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

import dk.apaq.peers.sip.RFC3261;
import dk.apaq.peers.sip.core.useragent.handlers.ByeHandler;
import dk.apaq.peers.sip.core.useragent.handlers.CancelHandler;
import dk.apaq.peers.sip.core.useragent.handlers.InviteHandler;
import dk.apaq.peers.sip.core.useragent.handlers.OptionsHandler;
import dk.apaq.peers.sip.core.useragent.handlers.RegisterHandler;
import dk.apaq.peers.sip.syntaxencoding.NameAddress;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderFieldValue;
import dk.apaq.peers.sip.syntaxencoding.SipHeaderParamName;
import dk.apaq.peers.sip.syntaxencoding.SipHeaders;
import dk.apaq.peers.sip.syntaxencoding.SipURI;
import dk.apaq.peers.sip.syntaxencoding.SipUriSyntaxException;
import dk.apaq.peers.sip.transaction.TransactionManager;
import dk.apaq.peers.sip.transactionuser.Dialog;
import dk.apaq.peers.sip.transactionuser.DialogManager;
import dk.apaq.peers.sip.transport.SipRequest;
import dk.apaq.peers.sip.transport.SipResponse;
import dk.apaq.peers.sip.transport.TransportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class RequestManager {

    private static final Logger LOG = LoggerFactory.getLogger(RequestManager.class);
    
    public static SipURI getDestinationUri(SipRequest sipRequest) {
        SipHeaders requestHeaders = sipRequest.getSipHeaders();
        SipURI destinationUri = null;
        SipHeaderFieldValue route = requestHeaders.get(
                new SipHeaderFieldName(RFC3261.HDR_ROUTE));
        if (route != null) {
            try {
                destinationUri = new SipURI(
                        NameAddress.nameAddressToUri(route.toString()));
            } catch (SipUriSyntaxException e) {
                LOG.error("syntax error", e);
            }
        }
        if (destinationUri == null) {
            destinationUri = sipRequest.getRequestUri();
        }
        return destinationUri;
    }

    public static SipResponse generateResponse(SipRequest sipRequest,
            Dialog dialog, int statusCode, String reasonPhrase) {
        //8.2.6.2
        SipResponse sipResponse = new SipResponse(statusCode, reasonPhrase);
        SipHeaders requestHeaders = sipRequest.getSipHeaders();
        SipHeaders responseHeaders = sipResponse.getSipHeaders();
        SipHeaderFieldName fromName = new SipHeaderFieldName(RFC3261.HDR_FROM);
        responseHeaders.add(fromName, requestHeaders.get(fromName));
        SipHeaderFieldName callIdName = new SipHeaderFieldName(RFC3261.HDR_CALLID);
        responseHeaders.add(callIdName, requestHeaders.get(callIdName));
        SipHeaderFieldName cseqName = new SipHeaderFieldName(RFC3261.HDR_CSEQ);
        responseHeaders.add(cseqName, requestHeaders.get(cseqName));
        SipHeaderFieldName viaName = new SipHeaderFieldName(RFC3261.HDR_VIA);
        responseHeaders.add(viaName, requestHeaders.get(viaName));//TODO check ordering
        SipHeaderFieldName toName = new SipHeaderFieldName(RFC3261.HDR_TO);
        SipHeaderFieldValue toValue = requestHeaders.get(toName);
        SipHeaderParamName toTagParamName = new SipHeaderParamName(RFC3261.PARAM_TAG);
        String toTag = toValue.getParam(toTagParamName);
        if (toTag == null) {
            if (dialog != null) {
                toTag = dialog.getLocalTag();
                toValue.addParam(toTagParamName, toTag);
            }
        }
        responseHeaders.add(toName, toValue);
        return sipResponse;
    }

    protected InviteHandler inviteHandler;
    protected CancelHandler cancelHandler;
    protected ByeHandler byeHandler;
    protected OptionsHandler optionsHandler;
    protected RegisterHandler registerHandler;
    
    protected UserAgent userAgent;
    protected TransactionManager transactionManager;
    protected TransportManager transportManager;
    protected Logger logger;
    
    public RequestManager(UserAgent userAgent, InviteHandler inviteHandler, CancelHandler cancelHandler, ByeHandler byeHandler,
            OptionsHandler optionsHandler, RegisterHandler registerHandler, DialogManager dialogManager, TransactionManager transactionManager,
            TransportManager transportManager) {
        this.userAgent = userAgent;
        this.inviteHandler = inviteHandler;
        this.cancelHandler = cancelHandler;
        this.byeHandler = byeHandler;
        this.optionsHandler = optionsHandler;
        this.registerHandler = registerHandler;
        this.transactionManager = transactionManager;
        this.transportManager = transportManager;
        this.logger = logger;
    }

    public InviteHandler getInviteHandler() {
        return inviteHandler;
    }

    public ByeHandler getByeHandler() {
        return byeHandler;
    }

    public RegisterHandler getRegisterHandler() {
        return registerHandler;
    }

}