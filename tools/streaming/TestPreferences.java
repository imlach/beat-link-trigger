import java.util.*;
import java.util.prefs.*;
public final class TestPreferences implements PreferencesFactory {
  private static final Preferences ROOT = new Node(null, "");
  public Preferences userRoot() { return ROOT; }
  public Preferences systemRoot() { return ROOT; }
  private static class Node extends AbstractPreferences {
    private final Map<String,String> values = new HashMap<>();
    Node(AbstractPreferences parent, String name) { super(parent,name); }
    protected void putSpi(String k,String v) { values.put(k,v); }
    protected String getSpi(String k) { return values.get(k); }
    protected void removeSpi(String k) { values.remove(k); }
    protected void removeNodeSpi() { values.clear(); }
    protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
    protected String[] childrenNamesSpi() { return new String[0]; }
    protected AbstractPreferences childSpi(String name) { return new Node(this,name); }
    protected void syncSpi() {}
    protected void flushSpi() {}
  }
}
