package filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/** Reduce tunnel traffic without caching account pages or buffering image files. */
@WebFilter("/*")
public class ResponseOptimizationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getServletPath().toLowerCase(Locale.ROOT);
        boolean image = path.matches(".*\\.(?:png|jpe?g|webp|gif|ico|woff2?)$");
        boolean staticAsset = path.startsWith("/assets/");
        if (staticAsset && image) {
            response.setHeader("Cache-Control", "public, max-age=3600");
        } else if (staticAsset && (path.endsWith(".css") || path.endsWith(".js"))) {
            // Revalidate changed code while allowing unchanged files to return 304.
            response.setHeader("Cache-Control", "public, max-age=0, must-revalidate");
        } else if (!"/product-image".equals(path)) {
            response.setHeader("Cache-Control", "no-store");
        }
        if (image || "/product-image".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        response.addHeader("Vary", "Accept-Encoding");
        if ("HEAD".equals(request.getMethod()) || request.getHeader("Range") != null
                || !acceptsGzip(request.getHeader("Accept-Encoding"))) {
            chain.doFilter(request, response);
            return;
        }
        BufferedResponse buffered = new BufferedResponse(response);
        chain.doFilter(request, buffered);
        buffered.finish();
    }

    private static boolean acceptsGzip(String header) {
        if (header == null) return false;
        Double gzip = null;
        Double wildcard = null;
        for (String entry : header.split(",")) {
            String[] parts = entry.trim().toLowerCase(Locale.ROOT).split(";");
            double quality = 1;
            for (int i = 1; i < parts.length; i++) {
                String parameter = parts[i].trim();
                if (parameter.startsWith("q=")) {
                    try {
                        quality = Double.parseDouble(parameter.substring(2).trim());
                        if (!Double.isFinite(quality) || quality < 0 || quality > 1) quality = 0;
                    } catch (NumberFormatException invalid) {
                        quality = 0;
                    }
                }
            }
            if ("gzip".equals(parts[0])) gzip = quality;
            if ("*".equals(parts[0])) wildcard = quality;
        }
        return gzip != null ? gzip > 0 : wildcard != null && wildcard > 0;
    }

    private static final class BufferedResponse extends HttpServletResponseWrapper {
        private static final int MAX_BUFFER_SIZE = 1024 * 1024;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream(4096);
        private final HttpServletResponse original;
        private PrintWriter writer;
        private String writerEncoding;
        private boolean streamRequested;
        private boolean passThrough;
        private boolean handled;
        private long declaredLength = -1;
        private final ServletOutputStream stream = new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) {
                throw new IllegalStateException("This filter supports synchronous responses only.");
            }
            @Override public void write(int value) throws IOException {
                if (!passThrough && body.size() >= MAX_BUFFER_SIZE) startPassThrough();
                if (passThrough) original.getOutputStream().write(value);
                else body.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                if (!passThrough && length > MAX_BUFFER_SIZE - body.size()) startPassThrough();
                if (passThrough) original.getOutputStream().write(bytes, offset, length);
                else body.write(bytes, offset, length);
            }
            @Override public void flush() throws IOException {
                if (passThrough) original.getOutputStream().flush();
            }
        };

        BufferedResponse(HttpServletResponse response) {
            super(response);
            original = response;
        }

        @Override public ServletOutputStream getOutputStream() {
            if (writer != null) throw new IllegalStateException("Writer already obtained.");
            streamRequested = true;
            return stream;
        }

        @Override public PrintWriter getWriter() throws IOException {
            if (streamRequested) throw new IllegalStateException("Output stream already obtained.");
            if (writer == null) {
                writerEncoding = getCharacterEncoding();
                writer = new PrintWriter(new OutputStreamWriter(stream, writerEncoding));
            }
            return writer;
        }

        @Override public void setCharacterEncoding(String encoding) {
            if (writer == null) super.setCharacterEncoding(encoding);
        }

        @Override public void setContentType(String type) {
            super.setContentType(type);
            if (writer != null) super.setCharacterEncoding(writerEncoding);
        }

        @Override public void setContentLength(int length) { declaredLength = length; }
        @Override public void setContentLengthLong(long length) { declaredLength = length; }
        @Override public void setHeader(String name, String value) {
            if ("Content-Length".equalsIgnoreCase(name)) {
                declaredLength = value == null ? -1 : Long.parseLong(value);
            } else super.setHeader(name, value);
        }
        @Override public void addHeader(String name, String value) {
            if ("Content-Length".equalsIgnoreCase(name)) setHeader(name, value);
            else super.addHeader(name, value);
        }
        @Override public void setIntHeader(String name, int value) {
            if ("Content-Length".equalsIgnoreCase(name)) declaredLength = value;
            else super.setIntHeader(name, value);
        }

        @Override public void resetBuffer() {
            if (writer != null) writer.flush();
            super.resetBuffer();
            body.reset();
        }

        @Override public void reset() {
            super.reset();
            body.reset();
            declaredLength = -1;
            writer = null;
            writerEncoding = null;
            streamRequested = false;
        }

        @Override public void sendRedirect(String location) throws IOException {
            body.reset();
            handled = true;
            super.sendRedirect(location);
        }

        @Override public void sendError(int status) throws IOException {
            body.reset();
            handled = true;
            super.sendError(status);
        }

        @Override public void sendError(int status, String message) throws IOException {
            body.reset();
            handled = true;
            super.sendError(status, message);
        }

        @Override public void flushBuffer() throws IOException {
            if (writer != null) writer.flush();
            startPassThrough();
            super.flushBuffer();
        }

        private void startPassThrough() throws IOException {
            if (passThrough) return;
            passThrough = true;
            if (declaredLength >= 0) original.setContentLengthLong(declaredLength);
            body.writeTo(original.getOutputStream());
            body.reset();
        }

        void finish() throws IOException {
            if (handled) return;
            if (writer != null) writer.flush();
            if (passThrough || original.isCommitted()) return;
            int status = getStatus();
            if (status < 200 || status == 204 || status == 205 || status == 304) return;
            byte[] bytes = body.toByteArray();
            String type = getContentType();
            type = type == null ? "" : type.toLowerCase(Locale.ROOT);
            boolean text = type.startsWith("text/") || type.startsWith("application/json")
                    || type.startsWith("application/javascript") || type.startsWith("application/xml");
            String cache = getHeader("Cache-Control");
            if (bytes.length >= 512 && text && getHeader("Content-Encoding") == null
                    && (cache == null || !cache.toLowerCase(Locale.ROOT).contains("no-transform"))) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                    gzip.write(bytes);
                }
                if (compressed.size() < bytes.length) {
                    bytes = compressed.toByteArray();
                    original.setHeader("Content-Encoding", "gzip");
                    String etag = original.getHeader("ETag");
                    if (etag != null && !etag.startsWith("W/")) original.setHeader("ETag", "W/" + etag);
                }
            }
            original.setContentLengthLong(bytes.length);
            original.getOutputStream().write(bytes);
        }
    }
}
