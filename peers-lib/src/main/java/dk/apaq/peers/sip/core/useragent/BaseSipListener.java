package dk.apaq.peers.sip.core.useragent;

import dk.apaq.peers.sip.transport.SipRequest;
import dk.apaq.peers.sip.transport.SipResponse;

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
