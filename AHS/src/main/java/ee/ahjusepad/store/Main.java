package ee.ahjusepad.store;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Ahjusepa poe HTTP-server.
 * Teenindab nii staatilist veebilehte kui ka toodete, päringute ja
 * soovi korral administraatori API-päringuid.
 */
public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern CONFIGURED_USER_PROPERTY =
            Pattern.compile("user\\.(\\d+)\\.(username|password|role)");
    private static final Pattern JSON_CREATED_AT =
            Pattern.compile("\"createdAt\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final Pattern JSON_INTEREST =
            Pattern.compile("\"interest\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final Pattern IP_ADDRESS = Pattern.compile("[0-9A-Fa-f:.]{1,45}");
    // API kaudu väljastatavad kategooriad. Hoia loend kooskõlas Catalogiga
    // ning index.html-is olevate filtrite ja päringuvormi valikutega.
    private static final List<String> CATEGORIES = List.of(
            "Ovens", "Fireplaces", "Fireplace inserts", "Ready-made fireplaces", "Stoves", "Accessories");
    // URL-i ja veebipaketti lisatud faili vastavus. Selgesõnaline loend
    // väldib suvaliste failide lugemist serveri klassiteelt.
    private static final Map<String, String> WEB_FILES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/styles.css", "styles.css",
            "/app.js", "app.js",
            "/ahjusepad-logo.png", "ahjusepad-logo.png",
            "/admin", "admin.html",
            "/admin.html", "admin.html",
            "/admin.css", "admin.css",
            "/admin.js", "admin.js"
    );

    private final InquiryStore inquiryStore;
    private final AdminAuth adminAuth;

    private Main(Path dataDirectory, AdminAuth adminAuth) throws IOException {
        this.inquiryStore = new InquiryStore(dataDirectory);
        this.adminAuth = adminAuth;
    }

    public static void main(String[] args) throws Exception {
        // Administraatorid saab määrata keskkonnamuutujate või välise kasutajafailiga.
        int port = parsePort(System.getenv().getOrDefault("PORT", args.length > 0 ? args[0] : "8080"));
        Path data = Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));
        String bindAddress = System.getenv().getOrDefault("BIND_ADDRESS", "127.0.0.1");
        InetAddress address = InetAddress.getByName(bindAddress);
        String adminUsername = System.getenv("ADMIN_USERNAME");
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        String adminUsersFile = System.getenv("ADMIN_USERS_FILE");
        boolean cookieSecure = parseBoolean(System.getenv().getOrDefault("ADMIN_COOKIE_SECURE", "false"),
                "ADMIN_COOKIE_SECURE");
        AdminAuth adminAuth = null;
        if (adminUsersFile != null && !adminUsersFile.isBlank()) {
            if (adminUsername != null || adminPassword != null) {
                throw new IllegalArgumentException(
                        "Use either ADMIN_USERS_FILE or ADMIN_USERNAME/ADMIN_PASSWORD, not both");
            }
            adminAuth = new AdminAuth(loadAdminUsers(Path.of(adminUsersFile)), cookieSecure);
        } else if (adminUsername != null || adminPassword != null) {
            if (adminUsername == null || adminPassword == null) {
                throw new IllegalArgumentException("ADMIN_USERNAME and ADMIN_PASSWORD must be configured together");
            }
            adminAuth = new AdminAuth(List.of(new AdminAuth.ConfiguredUser(adminUsername, adminPassword, "ADMIN")),
                    cookieSecure);
        }
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException(
                    "The server must bind to a loopback address; expose it through a trusted HTTPS reverse proxy");
        }
        Main app = new Main(data, adminAuth);

        HttpServer server = HttpServer.create(new InetSocketAddress(address, port), 64);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                Math.max(8, Runtime.getRuntime().availableProcessors() * 2),
                30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        server.createContext("/", app::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(2);
            executor.shutdown();
        }, "store-shutdown"));
        server.start();
        System.out.printf("Ahjusepad storefront running on http://%s:%d (data: %s)%n",
                address.getHostAddress(), port, data.toAbsolutePath().normalize());
    }

    private static List<AdminAuth.ConfiguredUser> loadAdminUsers(Path usersFile) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<Integer, Map<String, String>> configuredUsers = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            var matcher = CONFIGURED_USER_PROPERTY.matcher(key);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported admin user setting: " + key);
            }
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Admin user indexes must be between 1 and 100", ex);
            }
            if (index < 1 || index > 100) {
                throw new IllegalArgumentException("Admin user indexes must be between 1 and 100");
            }
            String field = matcher.group(2);
            String previous = configuredUsers.computeIfAbsent(index, ignored -> new LinkedHashMap<>())
                    .put(field, properties.getProperty(key));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate admin user setting: " + key);
            }
        }
        if (configuredUsers.isEmpty()) {
            throw new IllegalArgumentException("Admin user file must contain at least one user");
        }
        List<AdminAuth.ConfiguredUser> users = new ArrayList<>();
        for (int index = 1; index <= configuredUsers.size(); index++) {
            Map<String, String> user = configuredUsers.get(index);
            if (user == null || user.size() != 3) {
                throw new IllegalArgumentException(
                        "Admin user indexes must be consecutive and define username, password, and role");
            }
            users.add(new AdminAuth.ConfiguredUser(
                    user.get("username"), user.get("password"), user.get("role")));
        }
        return List.copyOf(users);
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("PORT must be 1–65535");
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("PORT must be a number", ex);
        }
    }

    private static boolean parseBoolean(String raw, String setting) {
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        throw new IllegalArgumentException(setting + " must be true or false");
    }

    private void handle(HttpExchange exchange) throws IOException {
        setSecurityHeaders(exchange.getResponseHeaders());
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);

            if (path.startsWith("/api/")) {
                handleApi(exchange, path, method);
                return;
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                return;
            }
            String resource = WEB_FILES.get(path);
            if (resource == null) {
                sendJson(exchange, 404, Map.of("error", "Not found"));
                return;
            }
            serveResource(exchange, resource, method.equals("HEAD"));
        } catch (BadRequest ex) {
            sendJson(exchange, ex.status, Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            // Ära saada kliendile serveri siseinfot ega klientide andmeid.
            try { sendJson(exchange, 500, Map.of("error", "Unable to complete request")); }
            catch (IOException ignored) { exchange.close(); }
            System.err.println("Request I/O failure: " + ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            try { sendJson(exchange, 500, Map.of("error", "Unexpected server error")); }
            catch (IOException ignored) { exchange.close(); }
            System.err.println("Request failure: " + ex.getClass().getSimpleName());
        }
    }

    /**
     * Suunab /api/ päringud nende lõpp-punktide töötlejatele.
     * Siin paiknevad tervisekontroll, kategooriad, tooteloend,
     * päringuvormi salvestamine ja valikuline administraatorivaade.
     */
    private void handleApi(HttpExchange exchange, String path, String method) throws IOException {
        if (path.equals("/api/health")) {
            if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            sendJson(exchange, 200, Map.of("status", "ok", "service", "ahjusepad-store", "time", Instant.now().toString()));
            return;
        }
        if (path.equals("/api/categories")) {
            if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            sendJson(exchange, 200, CATEGORIES);
            return;
        }
        if (path.equals("/api/products")) {
            if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            // Tagasta ainult avalikuks kuvamiseks vajalikud väljad.
            // Päringust saadud väärtusi piiratakse enne nende otsingus kasutamist.
            Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
            String type = limit(query.getOrDefault("type", "All"), 80, "type");
            String search = limit(query.getOrDefault("q", ""), 120, "q");
            List<Map<String, Object>> products = Catalog.search(type, search).stream().map(product -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", product.name()); p.put("type", product.type());
                p.put("detail", product.detail()); p.put("price", product.price());
                p.put("image", product.image()); p.put("badge", product.badge());
                p.put("description", product.description());
                return p;
            }).toList();
            sendJson(exchange, 200, Map.of("count", products.size(), "products", products));
            return;
        }
        if (path.equals("/api/inquiries")) {
            if (!method.equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
            // Kontrolli päringu päritolu, vormingu ja kohustuslikud väljad
            // enne, kui kliendi andmed faili salvestatakse.
            checkSameOriginIfProvided(exchange);
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                throw new BadRequest(415, "Content-Type must be application/x-www-form-urlencoded");
            }
            String body = readLimitedBody(exchange);
            Map<String, String> form = parseForm(body);
            if (!form.getOrDefault("website", "").isBlank()) {
                // Peidetud rämpspostilõks: robot saab tavapärase vastuse,
                // kuid automaatselt täidetud päringut ei salvestata.
                sendJson(exchange, 201, Map.of("status", "saved", "message", "Thank you. Your enquiry has been saved."));
                return;
            }
            String name = limit(form.getOrDefault("name", "").strip(), 100, "name");
            String email = limit(form.getOrDefault("email", "").strip(), 254, "email");
            String phone = limit(form.getOrDefault("phone", "").strip(), 40, "phone");
            String interest = limit(form.getOrDefault("interest", "General consultation").strip(), 100, "interest");
            String message = limit(form.getOrDefault("message", "").strip(), 3000, "message");
            if (name.length() < 2) throw new BadRequest(400, "Please enter your name (at least 2 characters).");
            if (!EMAIL.matcher(email).matches()) throw new BadRequest(400, "Please enter a valid email address.");
            if (!"true".equalsIgnoreCase(form.getOrDefault("consent", ""))) {
                throw new BadRequest(400, "Consent is required to submit an enquiry.");
            }
            Map<String, String> saved = inquiryStore.save(name, email, phone, interest, message);
            sendJson(exchange, 201, Map.of("status", "saved", "id", saved.get("id"),
                    "message", "Thank you. Your enquiry has been saved."));
            return;
        }
        if (path.equals("/api/admin/login")) {
            if (!method.equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
            requireAdminAuthConfigured();
            requireSameOrigin(exchange);
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                throw new BadRequest(415, "Content-Type must be application/x-www-form-urlencoded");
            }
            Map<String, String> form = parseForm(readLimitedBody(exchange));
            String username = limit(form.getOrDefault("username", ""), 100, "username");
            String password = limit(form.getOrDefault("password", ""), 256, "password");
            String remoteAddress = remoteAddress(exchange);
            try {
                String sessionToken = adminAuth.login(username, password, remoteAddress);
                AdminAuth.SessionInfo session = adminAuth.session(sessionToken);
                LOGGER.log(Level.INFO, "Admin event action=login user={0} remote={1}",
                        new Object[]{session.username(), remoteAddress});
                setAdminCookie(exchange, sessionToken, AdminAuth.SESSION_MAX_AGE_SECONDS);
                sendJson(exchange, 200, Map.of(
                        "status", "authenticated",
                        "username", session.username(),
                        "role", session.role(),
                        "csrfToken", session.csrfToken()));
            } catch (AdminAuth.InvalidCredentials ex) {
                LOGGER.log(Level.WARNING, "Admin event action=login_failed remote={0}", remoteAddress);
                sendJson(exchange, 401, Map.of("error", "Invalid username or password"));
            } catch (AdminAuth.LoginRateLimited ex) {
                LOGGER.log(Level.WARNING, "Admin event action=login_rate_limited remote={0}", remoteAddress);
                sendJson(exchange, 429, Map.of("error", "Too many login attempts. Try again later."));
            } catch (AdminAuth.SessionLimitReached ex) {
                sendJson(exchange, 503, Map.of("error", "The maximum number of admin sessions is active"));
            }
            return;
        }
        if (path.equals("/api/admin/session")) {
            if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            requireAdminAuthConfigured();
            String token = adminSessionToken(exchange);
            AdminAuth.SessionInfo session = token == null ? null : adminAuth.session(token);
            if (session == null) {
                sendJson(exchange, 200, Map.of("authenticated", false));
                return;
            }
            setAdminCookie(exchange, token, AdminAuth.SESSION_MAX_AGE_SECONDS);
            sendJson(exchange, 200, Map.of(
                    "authenticated", true,
                    "username", session.username(),
                    "role", session.role(),
                    "csrfToken", session.csrfToken()));
            return;
        }
        if (path.equals("/api/admin/logout")) {
            if (!method.equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
            requireAdminAuthConfigured();
            requireSameOrigin(exchange);
            String token = adminSessionToken(exchange);
            AdminAuth.SessionInfo session = token == null ? null : adminAuth.session(token);
            if (session != null) requireCsrfToken(exchange, session);
            String username = session == null ? "" : session.username();
            if (token != null && adminAuth.logout(token)) {
                LOGGER.log(Level.INFO, "Admin event action=logout user={0} remote={1}",
                        new Object[]{username, remoteAddress(exchange)});
            }
            setAdminCookie(exchange, "", 0);
            sendJson(exchange, 200, Map.of("status", "logged_out"));
            return;
        }
        if (path.equals("/api/admin/inquiries")) {
            if (!method.equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            requireAdminAuthConfigured();
            String token = adminSessionToken(exchange);
            AdminAuth.SessionInfo session = token == null ? null : adminAuth.session(token);
            if (session == null) {
                LOGGER.log(Level.WARNING, "Admin event action=unauthorized_inquiry_read remote={0}",
                        remoteAddress(exchange));
                throw new BadRequest(401, "Authentication required");
            }
            setAdminCookie(exchange, token, AdminAuth.SESSION_MAX_AGE_SECONDS);
            LOGGER.log(Level.INFO, "Admin event action=read_inquiries user={0} role={1} remote={2}",
                    new Object[]{session.username(), session.role(), remoteAddress(exchange)});
            List<String> lines = inquiryStore.allJsonLines();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) json.append(',');
                json.append(session.role().equals("ADMIN")
                        ? lines.get(i) : inquirySummaryJson(lines.get(i)));
            }
            sendRaw(exchange, 200, "application/json; charset=utf-8", json.append(']').toString());
            return;
        }
        sendJson(exchange, 404, Map.of("error", "Not found"));
    }

    private void requireAdminAuthConfigured() {
        if (adminAuth == null) throw new BadRequest(404, "Not found");
    }

    private static String adminSessionToken(HttpExchange exchange) {
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String cookie : header.split(";")) {
                String[] parts = cookie.strip().split("=", 2);
                if (parts.length == 2 && parts[0].equals(AdminAuth.COOKIE_NAME)) return parts[1];
            }
        }
        return null;
    }

    private static String inquirySummaryJson(String jsonLine) {
        var createdAt = JSON_CREATED_AT.matcher(jsonLine);
        var interest = JSON_INTEREST.matcher(jsonLine);
        if (!createdAt.find() || !interest.find()) {
            throw new IllegalStateException("Stored inquiry is missing summary fields");
        }
        return "{\"createdAt\":" + createdAt.group(1) + ",\"interest\":" + interest.group(1) + "}";
    }

    private static String remoteAddress(HttpExchange exchange) {
        String proxiedAddress = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (proxiedAddress != null && IP_ADDRESS.matcher(proxiedAddress).matches()) {
            return proxiedAddress;
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void setAdminCookie(HttpExchange exchange, String token, int maxAgeSeconds) {
        StringBuilder cookie = new StringBuilder(AdminAuth.COOKIE_NAME).append('=').append(token)
                .append("; Path=/api/admin; HttpOnly; SameSite=Strict; Max-Age=").append(maxAgeSeconds);
        if (adminAuth.cookieSecure()) cookie.append("; Secure");
        exchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
    }

    private static void requireSameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            throw new BadRequest(403, "Origin header is required");
        }
        final URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new BadRequest(403, "Invalid origin");
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        String forwardedProto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        String expectedScheme = forwardedProto == null ? "http" : forwardedProto.toLowerCase(Locale.ROOT);
        if (host == null || uri.getHost() == null || uri.getRawAuthority() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || !uri.getRawAuthority().equalsIgnoreCase(host)
                || !expectedScheme.equals("http") && !expectedScheme.equals("https")
                || !expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new BadRequest(403, "Cross-origin submission is not allowed");
        }
    }

    private static void requireCsrfToken(HttpExchange exchange, AdminAuth.SessionInfo session) {
        String supplied = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (supplied == null || !MessageDigest.isEqual(
                session.csrfToken().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequest(403, "A valid CSRF token is required");
        }
    }

    private static void checkSameOriginIfProvided(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) requireSameOrigin(exchange);
    }

    private static String readLimitedBody(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream input = exchange.getRequestBody()) {
            bytes = input.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) throw new BadRequest(413, "Form is too large");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Parsib URL-i päringuparameetrid ja vormi sisu
     * application/x-www-form-urlencoded vormingu reeglite järgi.
     */
    private static Map<String, String> parseForm(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int split = pair.indexOf('=');
            String key = decode(split < 0 ? pair : pair.substring(0, split));
            String value = decode(split < 0 ? "" : pair.substring(split + 1));
            if (key.length() > 100) throw new BadRequest(400, "Invalid form field");
            result.putIfAbsent(key, value);
        }
        return result;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { throw new BadRequest(400, "Invalid URL encoding"); }
    }

    private static String limit(String value, int max, String field) {
        // Eemalda ohtlikud juhtmärgid, mis võiksid rikkuda reahaaval salvestatud
        // JSONL-faili või muuta logide lugemise keeruliseks.
        String clean = value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        if (clean.length() > max) throw new BadRequest(400, "The " + field + " field is too long (max " + max + " characters).");
        return clean;
    }

    private static void serveResource(HttpExchange exchange, String filename, boolean headOnly) throws IOException {
        byte[] bytes;
        // Otsi faili rakendusse pakitud veebivarade hulgast, näiteks web/index.html.
        // Failinimi pärineb ainult ülal määratud WEB_FILES loendist.
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("web/" + filename)) {
            if (in == null) { sendJson(exchange, 500, Map.of("error", "Website assets are missing")); return; }
            bytes = in.readAllBytes();
        }
        String contentType = switch (filename.substring(filename.lastIndexOf('.') + 1)) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", filename.endsWith(".html")
                ? "no-cache" : "public, max-age=604800, stale-while-revalidate=86400");
        byte[] responseBytes = bytes;
        if (!filename.endsWith(".png")) {
            headers.set("Vary", "Accept-Encoding");
            if (acceptsGzip(exchange.getRequestHeaders())) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                    gzip.write(bytes);
                }
                responseBytes = compressed.toByteArray();
                headers.set("Content-Encoding", "gzip");
            }
        }
        headers.set("Content-Length", String.valueOf(responseBytes.length));
        if (headOnly) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        }
        exchange.close();
    }

    private static boolean acceptsGzip(Headers headers) {
        String acceptEncoding = headers.getFirst("Accept-Encoding");
        if (acceptEncoding == null) return false;
        Boolean gzipAccepted = null;
        boolean wildcardAccepted = false;
        for (String encoding : acceptEncoding.split(",")) {
            String[] parts = encoding.strip().split(";", 2);
            double quality = 1;
            if (parts.length > 1 && parts[1].strip().startsWith("q=")) {
                try {
                    quality = Double.parseDouble(parts[1].strip().substring(2));
                } catch (NumberFormatException ex) {
                    quality = 0;
                }
            }
            if (parts[0].strip().equalsIgnoreCase("gzip")) gzipAccepted = quality > 0;
            else if (parts[0].strip().equals("*")) wildcardAccepted = quality > 0;
        }
        return gzipAccepted != null ? gzipAccepted : wildcardAccepted;
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static void setSecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        headers.set("Content-Security-Policy", "default-src 'self'; img-src 'self' https://images.unsplash.com data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; script-src 'self'; connect-src 'self' https://formspree.io; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendRaw(exchange, status, "application/json; charset=utf-8", Json.value(body));
    }

    /**
     * Saadab vastuse kliendile. API vastused märgitakse vahemällu
     * salvestamise vältimiseks ning tekst kodeeritakse UTF-8 vormingus.
     */
    private static void sendRaw(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static final class BadRequest extends RuntimeException {
        private final int status;
        private BadRequest(int status, String message) { super(message); this.status = status; }
    }
}
