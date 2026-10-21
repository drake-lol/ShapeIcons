package lol.drake.shapeicons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Smart Launcher answers our igRequiresUpdate with igAuthorizedToUpdate (we are its active pack) or igNotAuthorizedToUpdate. */
public class AuthReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (IconGeneratorProvider.ACTION_AUTHORIZED.equals(i.getAction())) IconGeneratorProvider.notifyReady(c);
    }
}
