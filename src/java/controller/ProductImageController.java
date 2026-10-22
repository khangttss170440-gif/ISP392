package controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import service.ProductImageStore;

@WebServlet("/product-image")
public class ProductImageController extends HttpServlet {
    private ProductImageStore images;
    private static final byte[] PLACEHOLDER = ("<svg xmlns='http://www.w3.org/2000/svg' width='600' height='750' viewBox='0 0 600 750'><rect width='600' height='750' fill='#eee8df'/><path d='m220 230-75 55 45 85 35-20v210h150V350l35 20 45-85-75-55-35 30h-90Z' fill='#a18b71'/><text x='300' y='640' text-anchor='middle' font-family='Arial' font-size='18' fill='#635344'>MENSFORM</text></svg>").getBytes(StandardCharsets.UTF_8);

    @Override public void init() { images = ProductImageStore.from(getServletContext()); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, true);
    }

    @Override protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, false);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, boolean sendBody) throws IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        try {
            int id = Integer.parseInt(request.getParameter("productId"));
            String filename = request.getParameter("file");
            Path image = filename == null && request.getParameter("color") != null
                    ? images.findByColor(id, request.getParameter("color"))
                    : images.find(id, filename);
            if (image == null) {
                if (filename != null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                response.setContentType("image/svg+xml;charset=UTF-8");
                if (notModified(request, response, "\"mensform-placeholder-v1\"", false)) return;
                response.setContentLength(PLACEHOLDER.length);
                if (sendBody) response.getOutputStream().write(PLACEHOLDER);
                return;
            }
            response.setContentType(image.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg");
            long length = Files.size(image);
            String etag = "\"" + image.getFileName() + "-" + length + "-"
                    + Files.getLastModifiedTime(image).toMillis() + "\"";
            if (notModified(request, response, etag, filename != null && !filename.isBlank())) return;
            response.setContentLengthLong(length);
            if (sendBody) Files.copy(image, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private boolean notModified(HttpServletRequest request, HttpServletResponse response,
                                String etag, boolean immutable) {
        // File names change on replace; cover/color selection must always revalidate.
        response.setHeader("Cache-Control", immutable
                ? "public, max-age=86400, immutable" : "public, max-age=0, must-revalidate");
        response.setHeader("ETag", etag);
        String condition = request.getHeader("If-None-Match");
        if (condition != null) {
            for (String candidate : condition.split(",")) {
                candidate = candidate.trim();
                if (candidate.startsWith("W/")) candidate = candidate.substring(2);
                if ("*".equals(candidate) || etag.equals(candidate)) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return true;
                }
            }
        }
        return false;
    }
}
