package io.github.leekin2.xgoogle;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Uses the system's existing selection and picker; never changes the default itself. */
final class AssistantSelection {
    /** The assistant's app label, "" when none is selected, or null when the setting is not readable. */
    static String label(Context context) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), "assistant");
            if (value == null || value.isBlank())
                value = Settings.Secure.getString(context.getContentResolver(), "voice_interaction_service");
            if (value == null || value.isBlank()) return "";
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) return "";
            String name = component.getPackageName();
            try {
                PackageManager manager = context.getPackageManager();
                name = manager.getApplicationLabel(manager.getApplicationInfo(name, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
            return name;
        } catch (SecurityException failure) {
            return null;
        }
    }

    static void open(Activity activity) {
        Intent picker = new Intent().setComponent(new ComponentName("com.android.settings",
                "com.android.settings.Settings$DefaultAssistPickerActivity"));
        try { activity.startActivity(picker); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            activity.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        }
    }
    private AssistantSelection() { }
}
