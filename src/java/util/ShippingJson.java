package util;

import java.math.BigDecimal;
import java.util.*;

/** Small bounded JSON codec for carrier requests; no credentials are logged. */
public final class ShippingJson {
    private ShippingJson() { }
    public static String write(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map) {
            List<String> parts = new ArrayList<>();
            ((Map<?, ?>) v).forEach((k, x) -> parts.add(write(k.toString()) + ":" + write(x)));
            return "{" + String.join(",", parts) + "}";
        }
        if (v instanceof Iterable) {
            List<String> parts = new ArrayList<>();
            for (Object x : (Iterable<?>) v) parts.add(write(x));
            return "[" + String.join(",", parts) + "]";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : v.toString().toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    public static Object read(String s) {
        if (s == null || s.length() > 2_000_000) throw new IllegalArgumentException("Invalid carrier JSON.");
        Parser p = new Parser(s);
        Object v = p.value(0); p.space();
        if (p.i != s.length()) throw p.bad();
        return v;
    }
    @SuppressWarnings("unchecked") public static Map<String, Object> object(Object v) {
        if (!(v instanceof Map)) throw new IllegalArgumentException("Invalid carrier object.");
        return (Map<String, Object>) v;
    }
    @SuppressWarnings("unchecked") public static List<Object> array(Object v) {
        if (!(v instanceof List)) throw new IllegalArgumentException("Invalid carrier list.");
        return (List<Object>) v;
    }
    public static String string(Map<String, Object> m, String k) { Object v=m.get(k); return v==null?"":v.toString(); }
    public static int number(Map<String, Object> m, String k) { return new BigDecimal(string(m,k)).intValueExact(); }
    private static final class Parser {
        final String s; int i;
        Parser(String s) { this.s=s; }
        IllegalArgumentException bad() { return new IllegalArgumentException("Invalid carrier JSON."); }
        void space() { while(i<s.length() && " \r\n\t".indexOf(s.charAt(i))>=0) i++; }
        boolean eat(char c) { space(); if(i<s.length() && s.charAt(i)==c){i++; return true;} return false; }
        Object value(int depth) {
            if(depth>40) throw bad(); space(); if(i>=s.length()) throw bad(); char c=s.charAt(i);
            if(c=='"') return str();
            if(eat('{')) {
                Map<String,Object> m=new LinkedHashMap<>(); if(eat('}')) return m;
                do {space(); String k=str(); if(!eat(':')) throw bad();
                    if(m.containsKey(k)) throw bad(); m.put(k,value(depth+1));
                } while(eat(',')); if(!eat('}')) throw bad(); return m;
            }
            if(eat('[')) {
                List<Object> a=new ArrayList<>(); if(eat(']')) return a;
                do { a.add(value(depth+1)); } while(eat(',')); if(!eat(']')) throw bad(); return a;
            }
            for(String literal : new String[]{"true","false","null"}) if(s.startsWith(literal,i)) {
                i+=literal.length(); return literal.equals("null")?null:Boolean.valueOf(literal);
            }
            int start=i; while(i<s.length() && "-+0123456789.eE".indexOf(s.charAt(i))>=0) i++;
            String n=s.substring(start,i);
            if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw bad();
            return new BigDecimal(n);
        }
        String str() {
            if(i>=s.length() || s.charAt(i++)!='"') throw bad(); StringBuilder b=new StringBuilder();
            while(i<s.length()) {
                char c=s.charAt(i++); if(c=='"') return b.toString(); if(c<32) throw bad();
                if(c=='\\') {
                    if(i>=s.length()) throw bad(); char e=s.charAt(i++);
                    switch(e) {
                        case '"':case '\\':case '/':b.append(e);break;
                        case 'b':b.append('\b');break;case 'f':b.append('\f');break;
                        case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;
                        case 'u': if(i+4>s.length()) throw bad();
                            try { b.append((char)Integer.parseInt(s.substring(i,i+4),16)); } catch(NumberFormatException ex){throw bad();}
                            i+=4;break;
                        default:throw bad();
                    }
                } else b.append(c);
            } throw bad();
        }
    }
}
