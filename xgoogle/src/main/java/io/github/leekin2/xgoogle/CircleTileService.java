package io.github.leekin2.xgoogle;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Control center tile that opens Circle to Search. Only the system can start it, so the tile asks
 * XGoogle's SystemUI hook, which closes the control center first. Without the hook it opens Lens.
 */
public final class CircleTileService extends TileService {
    private static final String CIRCLE_TO_SEARCH = "io.github.leekin2.xgoogle.CIRCLE_TO_SEARCH";

    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override public void onClick() {
        if (isLocked()) unlockAndRun(this::ask);
        else ask();
    }

    private void ask() {
        Intent ask = new Intent(CIRCLE_TO_SEARCH).setPackage("com.android.systemui");
        sendOrderedBroadcast(ask, null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (getResultCode() == 1) return;
                Intent lens = new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens"))
                        .setPackage("com.google.android.googlequicksearchbox")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(PendingIntent.getActivity(CircleTileService.this, 0, lens,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
        }, null, 0, null, null);
    }
}
