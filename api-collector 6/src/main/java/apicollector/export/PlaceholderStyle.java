package apicollector.export;

/** Configurable placeholder wrapper. Postman/Bruno use {{name}}; others differ. */
public final class PlaceholderStyle {
    public final String prefix, suffix;
    public PlaceholderStyle(String prefix, String suffix) { this.prefix = prefix; this.suffix = suffix; }
    public static final PlaceholderStyle DOUBLE_CURLY = new PlaceholderStyle("{{", "}}");
    public String wrap(String name) { return prefix + name + suffix; }
}
