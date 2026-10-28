package controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import service.ProductImageStore;

@WebServlet("/product-image")
public class ProductImageController extends HttpServlet {
    private ProductImageStore images;
    private byte[] placeholder;

    @Override public void init() throws javax.servlet.ServletException {
        images = ProductImageStore.from(getServletContext());
        String path = util.AppConfig.require("PRODUCT_IMAGE_PLACEHOLDER_PATH");
        if (!path.startsWith("/assets/") || !path.endsWith(".svg") || path.contains(".."))
            throw new javax.servlet.ServletException("Invalid product placeholder asset path.");
        try (java.io.InputStream input = getServletContext().getResourceAsStream(path)) {
            if (input == null) throw new javax.servlet.ServletException("Product placeholder asset is missing.");
            placeholder = input.readAllBytes();
        } catch (IOException error) {
            throw new javax.servlet.ServletException("Could not read product placeholder asset.", error);
        }
    }

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
                if (notModified(request, response, "\"" + Integer.toHexString(java.util.Arrays.hashCode(placeholder)) + "\"", false)) return;
                response.setContentLength(placeholder.length);
                if (sendBody) response.getOutputStream().write(placeholder);
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
