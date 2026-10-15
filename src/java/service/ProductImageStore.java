package service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.ServletContext;
import javax.servlet.http.Part;

/** Persistent product photos, independent of the deployed WAR directory. */
public class ProductImageStore {
    public static final int MAX_IMAGES = 6;
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private final Path root;

    public ProductImageStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static ProductImageStore from(ServletContext context) {
        String configured = System.getenv("MENSFORM_PRODUCT_IMAGE_DIR");
        if (configured == null || configured.isBlank()) configured = context.getInitParameter("productImageDirectory");
        if (configured == null || configured.isBlank()) {
            configured = Paths.get(System.getProperty("catalina.base", "."), "mensform-data", "product-images").toString();
        }
        return new ProductImageStore(Paths.get(configured));
    }

    private Path directory(int productId) {
        if (productId <= 0) throw new IllegalArgumentException("Mã sản phẩm không hợp lệ.");
        return root.resolve(Integer.toString(productId));
    }

    public List<String> list(int productId) throws IOException {
        Path directory = directory(productId);
        if (!Files.exists(directory)) return new ArrayList<>();
        checkDirectory(directory);
        try (Stream<Path> files = Files.list(directory)) {
            List<String> names = files.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p))
                    .map(p -> p.getFileName().toString()).filter(ProductImageStore::isImageName)
                    .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
            Path cover = directory.resolve(".cover");
            if (Files.isRegularFile(cover) && !Files.isSymbolicLink(cover) && Files.size(cover) < 256) {
                String selected = Files.readString(cover).trim();
                if (names.remove(selected)) names.add(0, selected);
            }
            return names;
        }
    }

    public Path find(int productId, String name) throws IOException {
        List<String> names = list(productId);
        if (name == null || name.isEmpty()) name = names.isEmpty() ? null : names.get(0);
        if (name == null || !isImageName(name) || !names.contains(name)) return null;
        return directory(productId).resolve(name);
    }

    private static boolean isImageName(String name) {
        return name.matches("(?i)[a-z0-9][a-z0-9._-]*\\.(jpg|jpeg|png)");
    }

    private void checkDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !directory.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Invalid product image directory.");
        }
    }

    /** Validate every photo before changing product records. Do not trust MIME or filename. */
    public List<Upload> readUploads(Collection<Part> parts) throws IOException {
        List<Upload> uploads = new ArrayList<>();
        for (Part part : parts) {
            if (!"images".equals(part.getName()) || part.getSize() == 0) continue;
            if (uploads.size() >= MAX_IMAGES) throw new IllegalArgumentException("Mỗi sản phẩm tối đa 6 ảnh.");
            if (part.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("Mỗi ảnh tối đa 5 MB.");
            byte[] bytes;
            try (java.io.InputStream input = part.getInputStream()) {
                bytes = input.readNBytes((int) MAX_FILE_SIZE + 1);
            }
            if (bytes.length > MAX_FILE_SIZE) throw new IllegalArgumentException("Mỗi ảnh tối đa 5 MB.");
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw invalidImage();
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > 12_000_000L) {
                        throw new IllegalArgumentException("Ảnh tối đa 12 megapixel.");
                    }
                    java.awt.image.BufferedImage decoded = reader.read(0);
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    if (decoded == null || !ImageIO.write(decoded, format, output)) throw invalidImage();
                    uploads.add(new Upload(output.toByteArray(), format.equals("jpeg") ? "jpg" : "png"));
                } finally { reader.dispose(); }
            } catch (javax.imageio.IIOException e) { throw invalidImage(); }
        }
        return uploads;
    }

    private IllegalArgumentException invalidImage() {
        return new IllegalArgumentException("Chỉ nhận ảnh JPG hoặc PNG hợp lệ. Vui lòng chọn lại ảnh.");
    }

    public void checkCapacity(int productId, List<Upload> uploads) throws IOException {
        if (!uploads.isEmpty() && list(productId).size() + uploads.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Mỗi sản phẩm tối đa 6 ảnh. Hãy giảm số ảnh tải thêm.");
        }
    }

    public void save(int productId, List<Upload> uploads) throws IOException {
        synchronized (ProductImageStore.class) {
        checkCapacity(productId, uploads);
        Path directory = directory(productId);
        Files.createDirectories(directory);
        checkDirectory(directory);
        boolean first = list(productId).isEmpty();
        List<Path> written = new ArrayList<>();
        try {
            for (Upload upload : uploads) {
                String filename = (first ? "cover-" : "image-") + UUID.randomUUID() + "." + upload.extension;
                first = false;
                Path target = directory.resolve(filename);
                written.add(target);
                Files.write(target, upload.bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            for (Path target : written) {
                try { Files.deleteIfExists(target); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw e;
        }
        }
    }

    public void setCover(int productId, String name) throws IOException {
        synchronized (ProductImageStore.class) {
            requireImage(productId, name);
            Path directory = directory(productId);
            Path temporary = Files.createTempFile(directory, ".cover-", ".tmp");
            try {
                Files.writeString(temporary, name);
                Files.move(temporary, directory.resolve(".cover"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    public void delete(int productId, String name) throws IOException {
        synchronized (ProductImageStore.class) {
            java.util.Map<String, String> colors = imageColors(productId);
            Files.delete(requireImage(productId, name));
            if (colors.remove(name) != null) writeColors(productId, colors);
        }
    }

    public void replace(int productId, String name, List<Upload> uploads) throws IOException {
        synchronized (ProductImageStore.class) {
            Path old = requireImage(productId, name);
            if (uploads.size() != 1) throw new IllegalArgumentException("Hãy chọn đúng một ảnh để thay thế.");
            boolean wasCover = list(productId).get(0).equals(name);
            java.util.Map<String, String> colors = imageColors(productId);
            String oldColor = colors.remove(name);
            Upload upload = uploads.get(0);
            String newName = "image-" + UUID.randomUUID() + "." + upload.extension;
            Path replacement = directory(productId).resolve(newName);
            try {
                Files.write(replacement, upload.bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
                if (oldColor != null) {
                    colors.put(newName, oldColor);
                    writeColors(productId, colors);
                }
                if (wasCover) setCover(productId, newName);
                Files.delete(old);
            } catch (IOException e) {
                if (oldColor != null) {
                    colors.remove(newName);
                    colors.put(name, oldColor);
                    try { writeColors(productId, colors); } catch (IOException rollback) { e.addSuppressed(rollback); }
                }
                if (wasCover) {
                    try { setCover(productId, name); } catch (IOException rollback) { e.addSuppressed(rollback); }
                }
                try { Files.deleteIfExists(replacement); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }
    }

    public java.util.Map<String, String> imageColors(int productId) throws IOException {
        List<String> names = list(productId);
        java.util.Map<String, String> colors = new java.util.LinkedHashMap<>();
        Path file = directory(productId).resolve(".colors");
        if (Files.isRegularFile(file) && !Files.isSymbolicLink(file) && Files.size(file) < 65536) {
            java.util.Properties properties = new java.util.Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file)) { properties.load(reader); }
            for (String name : names) {
                String color = properties.getProperty(name);
                if (color != null && !color.isBlank()) colors.put(name, color);
            }
        }
        return colors;
    }

    public void setImageColor(int productId, String name, String color) throws IOException {
        synchronized (ProductImageStore.class) {
            requireImage(productId, name);
            String value = color == null ? "" : color.trim();
            if (value.length() > 100 || value.contains("\n") || value.contains("\r")) {
                throw new IllegalArgumentException("Màu ảnh không hợp lệ.");
            }
            java.util.Map<String, String> colors = imageColors(productId);
            if (value.isEmpty()) colors.remove(name); else colors.put(name, value);
            writeColors(productId, colors);
        }
    }

    private void writeColors(int productId, java.util.Map<String, String> colors) throws IOException {
        Path directory = directory(productId);
        java.util.Properties properties = new java.util.Properties();
        properties.putAll(colors);
        Path temporary = Files.createTempFile(directory, ".colors-", ".tmp");
        try {
            try (java.io.Writer writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Product photo colors"); }
            Files.move(temporary, directory.resolve(".colors"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public Path findByColor(int productId, String color) throws IOException {
        String value = color == null ? "" : color.trim();
        java.util.Map<String, String> colors = imageColors(productId);
        for (String name : list(productId)) {
            if (!value.isEmpty() && value.equalsIgnoreCase(colors.getOrDefault(name, "").trim())) return find(productId, name);
        }
        return find(productId, null);
    }

    private Path requireImage(int productId, String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Hãy chọn ảnh cần chỉnh sửa.");
        Path image = find(productId, name);
        if (image == null) throw new IllegalArgumentException("Ảnh không còn tồn tại. Vui lòng tải lại trang.");
        return image;
    }

    public static final class Upload {
        private final byte[] bytes;
        private final String extension;
        private Upload(byte[] bytes, String extension) { this.bytes = bytes; this.extension = extension; }
    }
}
