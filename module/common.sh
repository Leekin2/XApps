# Shared by customize.sh and service.sh: install the bundled XApps app (when this zip carries it)
# and enable it in LSPosed with its whole scope. Prints one line per step.
XAPPS_PKG=io.github.leekin2.xapps
LSPD_DB=/data/adb/lspd/config/modules_config.db
# The separate modules XApps replaces; XReply was merged into XNotify 4.0.
OLD_APPS="io.github.leekin2.xgoogle io.github.leekin2.xsecurity io.github.leekin2.xnotify io.github.leekin2.xreply"

# Copies an old app's own settings file under the name XApps uses, unless XApps has one already.
copy_prefs() {
  src=/data/data/$1/shared_prefs/$2.xml
  dir=/data/data/$XAPPS_PKG/shared_prefs
  dst=$dir/$3.xml
  [ -f "$src" ] && [ ! -f "$dst" ] || return 0
  uid=$(stat -c %u "/data/data/$XAPPS_PKG") || return 1
  # The app's own label, with its per-app categories (restorecon leaves those out).
  ctx=$(/system/bin/ls -dZ "/data/data/$XAPPS_PKG") || return 1
  ctx=${ctx%% *}
  mkdir -p "$dir" && chown "$uid:$uid" "$dir" && chmod 771 "$dir" && chcon "$ctx" "$dir" || return 1
  if ! { cp -f "$src" "$dst" && chown "$uid:$uid" "$dst" && chmod 660 "$dst" && chcon "$ctx" "$dst"; }; then
    rm -f "$dst"
    return 1
  fi
  echo "settings: copied $1 $2"
}

# Old XGoogle / XSecurity / XNotify / XReply apps: settings come over, then they are uninstalled.
# An app whose settings did not come over stays, because uninstalling it would lose them for good.
retire_old_apps() {
  for pkg in $OLD_APPS; do
    pm path "$pkg" > /dev/null 2>&1 || continue
    am force-stop "$pkg" 2>/dev/null
    copied=true
    case "$pkg" in
      *.xgoogle) copy_prefs "$pkg" xgoogle_ui xgoogle_ui || copied=false ;;
      *.xsecurity)
        copy_prefs "$pkg" switches xsecurity_switches || copied=false
        copy_prefs "$pkg" ui xsecurity_ui || copied=false ;;
    esac
    if [ "$copied" != true ]; then
      echo "old app: $pkg kept, its settings could not be copied. Uninstall it by hand"
      continue
    fi
    if pm uninstall "$pkg" > /dev/null 2>&1; then echo "old app: $pkg uninstalled"; else echo "old app: $pkg could not be uninstalled, remove it by hand"; fi
  done
}

xapps_setup() {
  apk_src="$1"
  tmp=/data/local/tmp/xapps-setup
  rm -rf "$tmp"; mkdir -p "$tmp"
  if [ -f "$apk_src" ]; then
    cp -f "$apk_src" "$tmp/xapps.apk"; chmod 644 "$tmp/xapps.apk"
    if ! out=$(pm install -r "$tmp/xapps.apk" 2>&1); then
      echo "app: install failed: $out"
      case "$out" in *SIGNATURES*|*UPDATE_INCOMPATIBLE*) echo "app: uninstall the XApps app first, then flash again" ;; esac
      rm -rf "$tmp"; return 1
    fi
    echo "app: installed"
  fi
  apk=$(pm path "$XAPPS_PKG" 2>/dev/null | grep base.apk | head -1 | sed 's/^package://')
  if [ -z "$apk" ]; then
    echo "app: not installed"
    rm -rf "$tmp"; return 1
  fi
  if [ ! -f "$LSPD_DB" ]; then
    echo "lsposed: not found, install LSPosed and enable XApps there"
    rm -rf "$tmp"; retire_old_apps; return 0
  fi
  pm list packages > "$tmp/packages.txt"
  # Before the old apps go: uninstalling one drops its LSPosed settings.
  res=$(app_process -Djava.class.path="$apk" /system/bin io.github.leekin2.xapps.ScopeWriter "$LSPD_DB" "$apk" 0 "$tmp/packages.txt" $OLD_APPS 2>&1)
  rm -rf "$tmp"
  case "$res" in
    ok*)
      set -- $res
      echo "lsposed: enabled, scope ticked ($2 new), old settings copied ($3)"
      retire_old_apps
      ;;
    *)
      echo "lsposed: could not write ($res), enable XApps and tick its scope in LSPosed"
      for pkg in $OLD_APPS; do
        pm path "$pkg" > /dev/null 2>&1 && echo "old app: $pkg kept, its LSPosed settings were not copied. Uninstall it by hand"
      done
      ;;
  esac
  return 0
}
