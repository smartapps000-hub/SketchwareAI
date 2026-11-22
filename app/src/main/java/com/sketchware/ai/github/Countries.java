package com.sketchware.ai.github;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * v2.1.4 (owner's 22 Nov review: "tapping a creator must show which country they are from: India, America, …, based on
 * location or something"): the country in a GitHub profile's own Location line ("Hyderabad, India", "Telangana", "SF,
 * USA", "London"). GitHub has no other public sign of where an account is; nothing is guessed from the phone, the
 * network or the language, and an empty or unknown Location says so. Country names come from the phone's own list
 * (Locale), plus the common short forms, states and big cities people write instead.
 */
public final class Countries {

    /** A country: ISO 3166 code, English name, flag. */
    public static final class Country {
        public final String code;
        public final String name;

        Country(String code, String name) {
            this.code = code;
            this.name = name;
        }

        /** The flag emoji (two regional indicator letters). */
        public String flag() {
            if (code == null || code.length() != 2) return "";
            int a = 0x1F1E6 + (Character.toUpperCase(code.charAt(0)) - 'A');
            int b = 0x1F1E6 + (Character.toUpperCase(code.charAt(1)) - 'A');
            return new String(Character.toChars(a)) + new String(Character.toChars(b));
        }

        @Override
        public String toString() {
            return flag() + " " + name;
        }
    }

    private static final Map<String, String> ALIASES = new HashMap<>();
    private static volatile Map<String, String> names;

    static {
        alias("US", "usa", "us", "u.s.", "u.s.a.", "u.s.a", "america", "united states of america", "united states",
                "california", "texas", "new york", "nyc", "san francisco", "seattle", "boston", "chicago",
                "los angeles", "austin", "washington dc", "washington, dc", "silicon valley", "bay area", "florida",
                "new jersey", "virginia", "massachusetts", "illinois", "denver", "atlanta", "miami");
        alias("GB", "uk", "u.k.", "united kingdom", "great britain", "britain", "england", "scotland", "wales",
                "northern ireland", "london", "manchester", "edinburgh", "birmingham, uk", "cambridge, uk", "oxford");
        alias("IN", "india", "bharat", "hindustan", "telangana", "andhra pradesh", "ap", "hyderabad", "secunderabad",
                "visakhapatnam", "vizag", "vijayawada", "guntur", "warangal", "tirupati", "nellore", "kurnool",
                "rajahmundry", "kakinada", "karimnagar", "khammam", "nizamabad", "bangalore", "bengaluru", "karnataka",
                "chennai", "tamil nadu", "madurai", "coimbatore", "mumbai", "maharashtra", "pune", "nagpur", "delhi",
                "new delhi", "noida", "gurgaon", "gurugram", "kolkata", "west bengal", "kerala", "kochi", "trivandrum",
                "thiruvananthapuram", "gujarat", "ahmedabad", "surat", "rajasthan", "jaipur", "uttar pradesh", "lucknow",
                "kanpur", "bihar", "patna", "odisha", "bhubaneswar", "punjab, india", "chandigarh", "madhya pradesh",
                "indore", "bhopal", "assam", "guwahati", "jharkhand", "ranchi", "goa", "uttarakhand", "dehradun");
        alias("AE", "uae", "u.a.e.", "dubai", "abu dhabi", "sharjah", "emirates");
        alias("SA", "ksa", "riyadh", "jeddah", "mecca", "makkah", "medina");
        alias("DE", "germany", "deutschland", "berlin", "munich", "münchen", "hamburg", "frankfurt", "cologne");
        alias("FR", "paris", "lyon", "marseille", "toulouse");
        alias("JP", "tokyo", "osaka", "kyoto");
        alias("CN", "china", "prc", "beijing", "shanghai", "shenzhen", "guangzhou", "hangzhou", "chengdu");
        alias("CA", "toronto", "vancouver", "montreal", "ottawa", "calgary", "ontario", "quebec", "british columbia");
        alias("AU", "sydney", "melbourne", "brisbane", "perth", "adelaide");
        alias("BD", "dhaka", "chittagong", "chattogram");
        alias("PK", "karachi", "lahore", "islamabad", "rawalpindi", "peshawar");
        alias("NP", "kathmandu", "pokhara");
        alias("LK", "colombo", "kandy");
        alias("ID", "jakarta", "bandung", "surabaya", "bali", "yogyakarta");
        alias("PH", "manila", "cebu", "quezon city");
        alias("VN", "hanoi", "ho chi minh", "ho chi minh city", "saigon", "da nang");
        alias("NG", "lagos", "abuja", "ibadan");
        alias("EG", "cairo", "alexandria", "giza");
        alias("KE", "nairobi", "mombasa");
        alias("BR", "brasil", "são paulo", "sao paulo", "rio de janeiro", "brasília", "brasilia", "belo horizonte");
        alias("RU", "russia", "moscow", "moskva", "saint petersburg", "st. petersburg");
        alias("TR", "türkiye", "turkiye", "istanbul", "ankara", "izmir");
        alias("KR", "south korea", "korea", "seoul", "busan");
        alias("SG", "singapore");
        alias("MY", "kuala lumpur", "penang");
        alias("TH", "bangkok", "chiang mai");
        alias("NL", "netherlands", "holland", "amsterdam", "rotterdam");
        alias("ES", "madrid", "barcelona", "valencia, spain");
        alias("IT", "rome", "roma", "milan", "milano", "naples");
        alias("MX", "mexico city", "cdmx", "guadalajara", "monterrey");
        alias("AR", "buenos aires");
        alias("ZA", "johannesburg", "cape town", "pretoria", "durban");
        alias("IR", "tehran");
        alias("IQ", "baghdad");
        alias("UA", "kyiv", "kiev", "kharkiv", "lviv");
        alias("PL", "warsaw", "kraków", "krakow", "wrocław");
        alias("IL", "tel aviv", "jerusalem");
        alias("QA", "doha");
        alias("KW", "kuwait city");
        alias("OM", "muscat");
        alias("MA", "casablanca", "rabat");
        alias("DZ", "algiers");
        alias("TN", "tunis");
        alias("ET", "addis ababa");
        alias("GH", "accra");
        alias("TZ", "dar es salaam");
        alias("UG", "kampala");
        alias("CO", "bogotá", "bogota", "medellín", "medellin");
        alias("PE", "lima");
        alias("CL", "santiago, chile");
        alias("NZ", "auckland", "wellington");
        alias("IE", "dublin");
        alias("SE", "stockholm");
        alias("CH", "zurich", "zürich", "geneva");
        alias("AT", "vienna", "wien");
        alias("BE", "brussels");
        alias("PT", "lisbon", "porto");
        alias("GR", "athens");
        alias("CZ", "prague", "czechia");
        alias("HU", "budapest");
        alias("RO", "bucharest");
        alias("MM", "myanmar", "burma", "yangon");
        alias("KH", "phnom penh");
        alias("AF", "kabul");
        alias("TW", "taipei", "taiwan");
        alias("HK", "hong kong");
    }

    private static void alias(String code, String... words) {
        for (String w : words) ALIASES.put(w, code);
    }

    private Countries() {
    }

    /** Every country's English name (lower case) → code, from the phone's own list, made once. */
    private static Map<String, String> names() {
        Map<String, String> n = names;
        if (n != null) return n;
        n = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
            if (name != null && !name.isEmpty()) n.put(name.toLowerCase(Locale.ROOT), code);
        }
        names = n;
        return n;
    }

    /** The English name of {@code code} ("IN" → "India"). */
    public static String nameOf(String code) {
        if ("US".equals(code)) return "United States";
        if ("GB".equals(code)) return "United Kingdom";
        String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
        return name == null || name.isEmpty() ? code : name;
    }

    /**
     * The country a profile's Location names, else null. The whole line is tried first, then its parts from the end
     * ("Hyderabad, Telangana, India" → India; "Guntur, AP" → India), each as a country name or a known place.
     */
    public static Country fromLocation(String location) {
        if (location == null) return null;
        String text = location.replace('\n', ' ').trim();
        if (text.isEmpty() || text.length() > 120) return null;
        String code = lookup(text);
        if (code == null) {
            String[] parts = text.split("[,/|·;\\-–—()]+");
            for (int i = parts.length - 1; i >= 0 && code == null; i--) code = lookup(parts[i]);
            // "Hyderabad India", "Berlin Germany": the last one or two words
            if (code == null) {
                String[] words = text.split("\\s+");
                if (words.length >= 2) code = lookup(words[words.length - 2] + " " + words[words.length - 1]);
                if (code == null && words.length >= 1) code = lookup(words[words.length - 1]);
                if (code == null && words.length >= 2) code = lookup(words[0]);
            }
        }
        return code == null ? null : new Country(code, nameOf(code));
    }

    private static String lookup(String part) {
        String p = part.trim().toLowerCase(Locale.ROOT).replaceAll("[.!🌍🌎🌏📍]+$", "").trim();
        if (p.isEmpty()) return null;
        String code = ALIASES.get(p);
        if (code != null) return code;
        code = names().get(p);
        if (code != null) return code;
        // a flag emoji alone ("🇮🇳")
        if (p.codePointCount(0, p.length()) == 2) {
            int a = p.codePointAt(0);
            int b = p.codePointAt(Character.charCount(a));
            if (a >= 0x1F1E6 && a <= 0x1F1FF && b >= 0x1F1E6 && b <= 0x1F1FF) {
                return "" + (char) ('A' + a - 0x1F1E6) + (char) ('A' + b - 0x1F1E6);
            }
        }
        return null;
    }
}
