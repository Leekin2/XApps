package io.github.leekin2.xgoogle;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Control center tile that opens Google Lens (QR codes included). */
public final class LensTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override public void onClick() {
        Intent lens = new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens"))
                .setPackage("com.google.android.googlequicksearchbox")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(this, 0, lens,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        unlockAndRun(() -> startActivityAndCollapse(pending));
    }
}
