package com.persona.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Persona — Static Controller
 * Serves frontend files (index.html, css, js) exactly like Flask's send_from_directory.
 */
@RestController
public class StaticController {

    @Value("${persona.frontend-dir:../frontend}")
    private String frontendDir;

    @GetMapping("/") // Only match the root path
    public ResponseEntity<Resource> index() {
        Path path = Paths.get(frontendDir, "index.html");
        File file = path.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().noStore().mustRevalidate())
            .contentType(MediaType.TEXT_HTML)
            .body(new FileSystemResource(file));
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> serveStatic(HttpServletRequest request) {
        String filepath = request.getRequestURI().substring(1); // remove leading slash
        
        // If it's an API route, this shouldn't match (API routes are handled by other controllers)
        if (filepath.startsWith("api/")) return ResponseEntity.notFound().build();

        Path path = Paths.get(frontendDir, filepath);
        File file = path.toFile();

        // If file not found and doesn't have an extension, try .html
        if (!file.exists() && !filepath.contains(".")) {
            path = Paths.get(frontendDir, filepath + ".html");
            file = path.toFile();
        }
        
        // If still not found, fallback to index.html for SPA routing
        if (!file.exists()) {
            path = Paths.get(frontendDir, "index.html");
            file = path.toFile();
        }

        if (!file.exists()) return ResponseEntity.notFound().build();

        String filename = file.getName().toLowerCase();
        CacheControl cacheControl;

        if (filename.endsWith(".html") || filename.contains("service-worker")) {
            cacheControl = CacheControl.noCache().noStore().mustRevalidate();
        } else if (filename.endsWith(".css") || filename.endsWith(".js") || filename.endsWith(".png") ||
                   filename.endsWith(".svg") || filename.endsWith(".ico") || filename.endsWith(".woff2") || 
                   filename.endsWith(".json")) {
            cacheControl = CacheControl.maxAge(Duration.ofDays(7)).cachePublic().staleWhileRevalidate(Duration.ofDays(1));
        } else {
            cacheControl = CacheControl.noCache().noStore().mustRevalidate(); // default
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
            .cacheControl(cacheControl)
            .contentType(mediaType)
            .body(new FileSystemResource(file));
    }
}
