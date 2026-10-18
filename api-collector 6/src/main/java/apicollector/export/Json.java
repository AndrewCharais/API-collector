package apicollector.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal, correct JSON writer + reader (no external dependency). */
public final class Json {
    private Json() {}

    // ---------- writer ----------
    public static String write(Object o) { StringBuilder sb = new StringBuilder(); write(o, sb, 0); return sb.toString(); }
    private static void write(Object o, StringBuilder sb, int d) {
        if (o == null) sb.append("null");
        else if (o instanceof String s) str(s, sb);
        else if (o instanceof Number || o instanceof Boolean) sb.append(o.toString());
        else if (o instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{"); int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ > 0) sb.append(",");
                nl(sb, d + 1); str(String.valueOf(e.getKey()), sb); sb.append(": "); write(e.getValue(), sb, d + 1);
            }
            nl(sb, d); sb.append("}");
        } else if (o instanceof List<?> l) {
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append("["); int i = 0;
            for (Object it : l) { if (i++ > 0) sb.append(","); nl(sb, d + 1); write(it, sb, d + 1); }
            nl(sb, d); sb.append("]");
        } else str(o.toString(), sb);
    }
    private static void nl(StringBuilder sb, int n) { sb.append('\n'); for (int i = 0; i < n; i++) sb.append("  "); }
    private static void str(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    // ---------- reader ----------
    /** Parse JSON text into Map / List / String / Double / Boolean / null. */
    public static Object read(String text) { return new P(text).top(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String text) {
        Object o = read(text);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    private static final class P {
        private final String s; private int i;
        P(String s) { this.s = s; }
        Object top() { Object v = value(); ws(); return v; }
        private Object value() {
            ws();
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't', 'f' -> bool();
                case 'n' -> nul();
                default -> number();
            };
        }
        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>(); expect('{'); ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws(); String k = string(); ws(); expect(':');
                m.put(k, value()); ws();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw err("expected , or }");
            }
            return m;
        }
        private List<Object> array() {
            List<Object> a = new ArrayList<>(); expect('['); ws();
            if (peek() == ']') { i++; return a; }
            while (true) {
                a.add(value()); ws();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw err("expected , or ]");
            }
            return a;
        }
        private String string() {
            expect('"'); StringBuilder b = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> b.append('"');   case '\\' -> b.append('\\');
                        case '/' -> b.append('/');   case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');  case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');  case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> throw err("bad escape");
                    }
                } else b.append(c);
            }
            return b.toString();
        }
        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }
        private Boolean bool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw err("bad literal");
        }
        private Object nul() { if (s.startsWith("null", i)) { i += 4; return null; } throw err("bad literal"); }
        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private char peek() { ws(); return i < s.length() ? s.charAt(i) : '\0'; }
        private char next() { return s.charAt(i++); }
        private void expect(char c) { ws(); if (next() != c) throw err("expected " + c); }
        private RuntimeException err(String m) { return new IllegalArgumentException("JSON @" + i + ": " + m); }
    }
}
