package net.sourceforge.peers.sip.core.useragent;

import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;

/**
 *
 * @author krog
 */
public class BaseSipListener implements SipListener {

    public void registering(SipRequest sipRequest) {
    }

    public void registerSuccessful(SipResponse sipResponse) {
    }

    public void registerFailed(SipResponse sipResponse) {
    }

    public void incomingCall(SipRequest sipRequest, SipResponse provResponse) {
    }

    public void remoteHangup(SipRequest sipRequest) {
    }

    public void ringing(SipResponse sipResponse) {
    }

    public void calleePickup(SipResponse sipResponse) {
    }

    public void error(SipResponse sipResponse) {
    }
    
}
