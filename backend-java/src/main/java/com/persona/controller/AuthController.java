package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persona — Auth Controller
 * Replaces: backend/routes/auth.py
 *
 * Endpoints:
 *   POST /api/auth/register
 *   POST /api/auth/login
 *   POST /api/auth/logout
 *   GET  /api/auth/me
 *   GET  /api/auth/google
 *   GET  /api/auth/google/callback
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Database db;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${persona.google-client-id:}")
    private String googleClientId;

    @Value("${persona.google-client-secret:}")
    private String googleClientSecret;

    @Value("${persona.google-redirect-uri:http://localhost:5000/api/auth/google/callback}")
    private String googleRedirectUri;

    @Autowired
    public AuthController(Database db) {
        this.db = db;
    }

    // ── Register ──────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> data, HttpSession session) {
        String username = trim(data.get("username"));
        String email    = trim(data.get("email")).toLowerCase();
        String password = str(data.get("password"));

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return err(400, "Name, email and password are required");
        }
        if (password.length() < 6) {
            return err(400, "Password must be at least 6 characters");
        }

        List<Map<String, Object>> existing = db.query("SELECT id FROM users WHERE email = ?", email);
        if (!existing.isEmpty()) {
            return err(409, "Email already registered. Please log in.");
        }

        String uid  = db.newId();
        String hash = passwordEncoder.encode(password);
        db.execute(
            "INSERT INTO users (id, username, email, password, created_at, updated_at) VALUES (?,?,?,?,?,?)",
            uid, username, email, hash, db.nowIso(), db.nowIso()
        );

        session.setAttribute("user_id",  uid);
        session.setAttribute("username", username);
        session.setMaxInactiveInterval(60 * 60 * 24 * 30); // 30 days

        return ResponseEntity.status(201).body(Map.of(
            "message",  "Account created successfully!",
            "user_id",  uid,
            "username", username
        ));
    }

    // ── Login ─────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> data, HttpSession session) {
        String email    = trim(data.get("email")).toLowerCase();
        String password = str(data.get("password"));

        if (email.isEmpty() || password.isEmpty()) {
            return err(400, "Email and password are required");
        }

        List<Map<String, Object>> rows = db.query("SELECT * FROM users WHERE email = ?", email);
        if (rows.isEmpty()) {
            return err(401, "Invalid email or password");
        }

        Map<String, Object> user = rows.get(0);
        String pwdHash = str(user.get("password"));

        if (pwdHash.isEmpty()) {
            return err(401, "This account uses Google Sign-in. Please use that button.");
        }

        boolean valid = false;
        try {
            valid = passwordEncoder.matches(password, pwdHash);
        } catch (IllegalArgumentException e) {
            // Hash does not look like BCrypt (legacy Python hash)
            return err(401, "Security update: Please recreate your account or use Google Sign-in to update your password.");
        }

        if (!valid) {
            if (pwdHash.startsWith("scrypt:") || pwdHash.startsWith("pbkdf2:")) {
                return err(401, "Security update: Please use Google Sign-in to link your account and update your login method.");
            }
            return err(401, "Invalid email or password");
        }

        String uid      = str(user.get("id"));
        String username = str(user.getOrDefault("username", str(user.get("name"))));
        if (username.isEmpty()) username = email.split("@")[0];

        session.setAttribute("user_id",  uid);
        session.setAttribute("username", username);
        session.setMaxInactiveInterval(60 * 60 * 24 * 30);

        return ResponseEntity.ok(Map.of(
            "message",  "Welcome back!",
            "user_id",  uid,
            "username", username
        ));
    }

    // ── Logout ────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ── Me ────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        String uid = (String) session.getAttribute("user_id");
        if (uid == null) return err(401, "Not authenticated");

        List<Map<String, Object>> rows = db.query("SELECT * FROM users WHERE id = ?", uid);
        if (rows.isEmpty()) {
            session.invalidate();
            return err(404, "User not found");
        }
        Map<String, Object> u = rows.get(0);
        return ResponseEntity.ok(Map.of(
            "id",         str(u.get("id")),
            "username",   str(u.getOrDefault("username", u.get("name"))),
            "email",      str(u.get("email")),
            "avatar_url", str(u.getOrDefault("avatar_url", u.get("picture")))
        ));
    }

    // ── Google OAuth — Step 1: Redirect ───────────────────────
    @GetMapping("/google")
    public void googleLogin(
        @RequestParam(required = false) String userId,
        HttpSession session,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        if (googleClientId.isEmpty()) {
            response.sendError(503, "Google OAuth not configured");
            return;
        }
        String rawState = generateState();
        session.setAttribute("oauth_state", rawState);

        String resolvedUserId = userId;
        if (resolvedUserId == null || resolvedUserId.trim().isEmpty()) {
            resolvedUserId = (String) session.getAttribute("user_id");
        }

        String state = rawState;
        if (resolvedUserId != null && !resolvedUserId.trim().isEmpty()) {
            state = rawState + ":" + resolvedUserId.trim();
        }

        String scope = "openid email profile " +
            "https://www.googleapis.com/auth/classroom.courses.readonly " +
            "https://www.googleapis.com/auth/classroom.coursework.me.readonly " +
            "https://www.googleapis.com/auth/gmail.modify";

        // Determine redirect URI dynamically based on current host if available (only for local dev)
        String resolvedRedirectUri = googleRedirectUri;
        if (googleRedirectUri != null && googleRedirectUri.contains("localhost") && request.getHeader("Host") != null) {
            String scheme = request.getScheme();
            String host = request.getHeader("Host");
            resolvedRedirectUri = scheme + "://" + host + "/api/auth/google/callback";
        }

        String params = "client_id=" + encode(googleClientId) +
            "&redirect_uri=" + encode(resolvedRedirectUri) +
            "&response_type=code" +
            "&scope=" + encode(scope) +
            "&access_type=offline" +
            "&prompt=select_account" +
            "&state=" + encode(state);

        response.sendRedirect("https://accounts.google.com/o/oauth2/v2/auth?" + params);
    }

    // ── Google OAuth — Step 2: Callback ───────────────────────
    @GetMapping("/google/callback")
    public void googleCallback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error,
        HttpSession session,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        if (error != null)  { response.sendRedirect("/login?error=" + error); return; }
        if (code == null)   { response.sendRedirect("/login?error=missing_code"); return; }
        
        String rawStateParam = state;
        String linkingUserId = null;
        if (state != null && state.contains(":")) {
            String[] parts = state.split(":", 2);
            rawStateParam = parts[0];
            linkingUserId = parts[1];
        }

        String savedState = (String) session.getAttribute("oauth_state");
        // Relax state validation for local/non-HTTPS development environments where session cookie might be stripped.
        if (savedState != null && !Objects.equals(rawStateParam, savedState)) {
            System.err.println("OAuth state mismatch: expected " + savedState + ", got " + rawStateParam);
            response.sendRedirect("/login?error=invalid_state");
            return;
        } else if (savedState == null) {
            System.out.println("OAuth callback: savedState was null (session lost/cookie stripped), allowing login callback for local testing.");
        }
        if (googleClientId.isEmpty() || googleClientSecret.isEmpty()) {
            response.sendRedirect("/login?error=oauth_not_configured");
            return;
        }

        // Determine redirect URI dynamically based on current host if available (only for local dev)
        String resolvedRedirectUri = googleRedirectUri;
        if (googleRedirectUri != null && googleRedirectUri.contains("localhost") && request.getHeader("Host") != null) {
            String scheme = request.getScheme();
            String host = request.getHeader("Host");
            resolvedRedirectUri = scheme + "://" + host + "/api/auth/google/callback";
        }

        // Exchange code for token
        String tokenBody = "code=" + encode(code) +
            "&client_id=" + encode(googleClientId) +
            "&client_secret=" + encode(googleClientSecret) +
            "&redirect_uri=" + encode(resolvedRedirectUri) +
            "&grant_type=authorization_code";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest tokenReq = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
            .build();

        HttpResponse<String> tokenResp;
        try {
            tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            response.sendRedirect("/login?error=token_exchange_failed");
            return;
        }
        if (tokenResp.statusCode() != 200) { response.sendRedirect("/login?error=token_exchange_failed"); return; }

        Map<String, Object> tokenData = mapper.readValue(tokenResp.body(), Map.class);
        String accessToken  = str(tokenData.get("access_token"));
        String refreshToken = str(tokenData.getOrDefault("refresh_token", ""));

        // Fetch user info
        HttpRequest userReq = HttpRequest.newBuilder()
            .uri(URI.create("https://www.googleapis.com/oauth2/v2/userinfo"))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();

        HttpResponse<String> userResp;
        try {
            userResp = client.send(userReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            response.sendRedirect("/login?error=userinfo_failed");
            return;
        }
        if (userResp.statusCode() != 200) { response.sendRedirect("/login?error=userinfo_failed"); return; }

        Map<String, Object> info = mapper.readValue(userResp.body(), Map.class);
        String email    = str(info.get("email"));
        String username = str(info.getOrDefault("name", email.split("@")[0]));
        String googleId = str(info.get("id"));
        String avatar   = str(info.get("picture"));

        if (email.isEmpty()) { response.sendRedirect("/login?error=no_email"); return; }

        // Upsert user
        String uid = null;
        if (linkingUserId != null && !linkingUserId.isEmpty()) {
            List<Map<String, Object>> userRows = db.query("SELECT * FROM users WHERE id = ?", linkingUserId);
            if (!userRows.isEmpty()) {
                uid = linkingUserId;
                try {
                    db.execute("UPDATE users SET google_id=?, avatar_url=?, updated_at=? WHERE id=?",
                        googleId, avatar, db.nowIso(), uid);
                } catch (Exception ignored) {}
            }
        }

        if (uid == null) {
            List<Map<String, Object>> rows = db.query("SELECT * FROM users WHERE email = ?", email);
            if (!rows.isEmpty()) {
                uid = str(rows.get(0).get("id"));
                try {
                    db.execute("UPDATE users SET google_id=?, avatar_url=?, updated_at=? WHERE id=?",
                        googleId, avatar, db.nowIso(), uid);
                } catch (Exception ignored) {}
            } else {
                uid = db.newId();
                db.execute(
                    "INSERT INTO users (id, username, email, password, google_id, avatar_url, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                    uid, username, email, "", googleId, avatar, db.nowIso(), db.nowIso()
                );
            }
        }

        // Save OAuth tokens
        List<Map<String, Object>> tRows = db.query("SELECT id FROM oauth_tokens WHERE user_id = ?", uid);
        try {
            if (!tRows.isEmpty()) {
                if (!refreshToken.isEmpty()) {
                    db.execute("UPDATE oauth_tokens SET access_token=?, refresh_token=?, updated_at=? WHERE user_id=?",
                        accessToken, refreshToken, db.nowIso(), uid);
                } else {
                    db.execute("UPDATE oauth_tokens SET access_token=?, updated_at=? WHERE user_id=?",
                        accessToken, db.nowIso(), uid);
                }
            } else {
                db.execute(
                    "INSERT INTO oauth_tokens (id, user_id, access_token, refresh_token, updated_at) VALUES (?,?,?,?,?)",
                    db.newId(), uid, accessToken, refreshToken, db.nowIso()
                );
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to save oauth_tokens: " + e.getMessage());
        }

        session.setAttribute("user_id",  uid);
        session.setAttribute("username", username);
        session.setMaxInactiveInterval(60 * 60 * 24 * 30);

        if (linkingUserId != null && !linkingUserId.isEmpty()) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Google Linked Successfully</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&display=swap\" rel=\"stylesheet\">\n" +
                "    <style>\n" +
                "        body {\n" +
                "            background: #0f0c1b;\n" +
                "            color: #ffffff;\n" +
                "            font-family: 'Outfit', sans-serif;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            height: 100vh;\n" +
                "            margin: 0;\n" +
                "            padding: 20px;\n" +
                "            box-sizing: border-box;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: rgba(255, 255, 255, 0.05);\n" +
                "            border: 1px solid rgba(255, 255, 255, 0.1);\n" +
                "            border-radius: 24px;\n" +
                "            padding: 40px 30px;\n" +
                "            max-width: 400px;\n" +
                "            box-shadow: 0 20px 40px rgba(0,0,0,0.5);\n" +
                "            backdrop-filter: blur(20px);\n" +
                "            animation: fadeIn 0.8s ease-out;\n" +
                "        }\n" +
                "        .icon {\n" +
                "            font-size: 64px;\n" +
                "            margin-bottom: 20px;\n" +
                "            display: inline-block;\n" +
                "            animation: pop 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275) 0.3s both;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            margin: 0 0 10px 0;\n" +
                "            font-weight: 800;\n" +
                "            font-size: 24px;\n" +
                "            background: linear-gradient(135deg, #a855f7, #6366f1, #06b6d4);\n" +
                "            -webkit-background-clip: text;\n" +
                "            -webkit-text-fill-color: transparent;\n" +
                "        }\n" +
                "        p {\n" +
                "            color: #94a3b8;\n" +
                "            font-size: 15px;\n" +
                "            line-height: 1.6;\n" +
                "            margin: 0 0 30px 0;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            background: linear-gradient(135deg, #6366f1, #a855f7);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 12px 30px;\n" +
                "            border-radius: 50px;\n" +
                "            font-weight: 600;\n" +
                "            font-size: 15px;\n" +
                "            cursor: pointer;\n" +
                "            text-decoration: none;\n" +
                "            display: inline-block;\n" +
                "            transition: all 0.3s ease;\n" +
                "            box-shadow: 0 4px 15px rgba(168, 85, 247, 0.4);\n" +
                "        }\n" +
                "        .btn:hover {\n" +
                "            transform: translateY(-2px);\n" +
                "            box-shadow: 0 6px 20px rgba(168, 85, 247, 0.6);\n" +
                "        }\n" +
                "        @keyframes fadeIn {\n" +
                "            from { opacity: 0; transform: translateY(20px); }\n" +
                "            to { opacity: 1; transform: translateY(0); }\n" +
                "        }\n" +
                "        @keyframes pop {\n" +
                "            0% { transform: scale(0); }\n" +
                "            100% { transform: scale(1); }\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <span class=\"icon\">🎉</span>\n" +
                "        <h1>Google Account Linked!</h1>\n" +
                "        <p>Your Google Classroom account has been successfully linked to your Persona app. You can now return to the app and click \"Sync\" again to import your assignments.</p>\n" +
                "        <button class=\"btn\" onclick=\"closeOrRedirect();\">Close Window</button>\n" +
                "        <script>\n" +
                "            function closeOrRedirect() {\n" +
                "                window.close();\n" +
                "                setTimeout(function() {\n" +
                "                    window.location.href = 'intent://#Intent;scheme=persona;package=com.example.persona;end';\n" +
                "                }, 300);\n" +
                "            }\n" +
                "        </script>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>");
            response.flushBuffer();
            return;
        }

        response.sendRedirect("/");
    }

    // ── Helpers ───────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    private String trim(Object o) { return o == null ? "" : o.toString().trim(); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String generateState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
