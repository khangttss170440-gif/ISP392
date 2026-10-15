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

    @Override public void init() { images = ProductImageStore.from(getServletContext()); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-cache");
        try {
            int id = Integer.parseInt(request.getParameter("productId"));
            Path image = request.getParameter("file") == null && request.getParameter("color") != null
                    ? images.findByColor(id, request.getParameter("color"))
                    : images.find(id, request.getParameter("file"));
            if (image == null) {
                if (request.getParameter("file") != null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                response.setContentType("image/svg+xml");
                response.getWriter().write("<svg xmlns='http://www.w3.org/2000/svg' width='600' height='750' viewBox='0 0 600 750'><rect width='600' height='750' fill='#eee8df'/><path d='m220 230-75 55 45 85 35-20v210h150V350l35 20 45-85-75-55-35 30h-90Z' fill='#a18b71'/><text x='300' y='640' text-anchor='middle' font-family='Arial' font-size='18' fill='#635344'>MENSFORM</text></svg>");
                return;
            }
            response.setContentType(image.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg");
            response.setContentLengthLong(Files.size(image));
            Files.copy(image, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
