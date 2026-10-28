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
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.servlet.ServletContext;
import javax.servlet.http.Part;

/** Persistent product photos, independent of the deployed WAR directory. */
public class ProductImageStore {
    public static final int MAX_IMAGES = util.AppConfig.integer("PRODUCT_IMAGE_MAX_COUNT", 1, 100);
    public static final long MAX_FILE_SIZE = util.AppConfig.integer("PRODUCT_IMAGE_MAX_FILE_BYTES", 1, Integer.MAX_VALUE - 1);
    public static final long MAX_REQUEST_SIZE = util.AppConfig.integer("PRODUCT_IMAGE_MAX_REQUEST_BYTES", 1, Integer.MAX_VALUE);
    public static final long MAX_PIXELS = util.AppConfig.integer("PRODUCT_IMAGE_MAX_PIXELS", 1, Integer.MAX_VALUE);
    private static final int MAX_ENCODED_SIZE = util.AppConfig.integer("PRODUCT_IMAGE_MAX_ENCODED_BYTES", 1, Integer.MAX_VALUE);
    private static final float JPEG_QUALITY = configuredQuality();
    private final Path root;

    private static float configuredQuality() {
        float value = util.AppConfig.decimal("PRODUCT_IMAGE_JPEG_QUALITY").floatValue();
        if (value <= 0 || value > 1) throw new IllegalStateException("PRODUCT_IMAGE_JPEG_QUALITY must be in (0, 1].");
        return value;
    }

    public ProductImageStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static ProductImageStore from(ServletContext context) {
        String configured = util.AppConfig.get("MENSFORM_PRODUCT_IMAGE_DIR");
        if (configured == null || configured.isBlank()) configured = context.getInitParameter("productImageDirectory");
        if (configured == null || configured.isBlank()) {
            configured = Paths.get(System.getProperty("catalina.base", "."), "mensform-data", "product-images").toString();
        }
        return new ProductImageStore(resolve(context, Paths.get(configured)));
    }

    /** A relative setting (e.g. "product-images") is looked up beside the deployed app and its parent
     *  folders, so a cloned project finds its own photos without a machine-specific path. */
    static Path resolve(ServletContext context, Path configured) {
        if (configured.isAbsolute()) return configured;
        String deployed = context.getRealPath("/");
        if (deployed != null) {
            for (Path dir = Paths.get(deployed).toAbsolutePath(); dir != null; dir = dir.getParent()) {
                Path candidate = dir.resolve(configured);
                if (Files.isDirectory(candidate)) return candidate;
            }
        }
        return Paths.get(System.getProperty("catalina.base", ".")).resolve(configured);
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
        long encodedTotal = 0;
        for (Part part : parts) {
            if (!"images".equals(part.getName()) || part.getSize() == 0) continue;
            if (uploads.size() >= MAX_IMAGES) throw new IllegalArgumentException("Mỗi sản phẩm tối đa " + MAX_IMAGES + " ảnh.");
            if (part.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("Mỗi ảnh tối đa " + MAX_FILE_SIZE + " byte.");
            byte[] bytes;
            try (java.io.InputStream input = part.getInputStream()) {
                bytes = input.readNBytes((int) MAX_FILE_SIZE + 1);
            }
            if (bytes.length > MAX_FILE_SIZE) throw new IllegalArgumentException("Mỗi ảnh tối đa " + MAX_FILE_SIZE + " byte.");
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw invalidImage();
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                        throw new IllegalArgumentException("Ảnh tối đa " + MAX_PIXELS + " pixel.");
                    }
                    java.awt.image.BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw invalidImage();
                    try {
                        byte[] encoded = encode(decoded, format);
                        encodedTotal += encoded.length;
                        if (encodedTotal > MAX_IMAGES * MAX_FILE_SIZE)
                            throw new IllegalArgumentException("Tổng ảnh sau xử lý vượt " + (MAX_IMAGES * MAX_FILE_SIZE) + " byte. Vui lòng tải ít ảnh hơn mỗi lần.");
                        uploads.add(new Upload(encoded, format.equals("jpeg") ? "jpg" : "png"));
                    } finally { decoded.flush(); }
                } finally { reader.dispose(); }
            } catch (javax.imageio.IIOException e) { throw invalidImage(); }
        }
        return uploads;
    }

    /** Keep the original pixel dimensions. PNG stays lossless; JPEG avoids default low quality/chroma subsampling. */
    private byte[] encode(java.awt.image.BufferedImage image, String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw invalidImage();
        ImageWriter writer = writers.next();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream() {
            private void checkSize(int extra) {
                if (extra < 0 || (long) count + extra > MAX_ENCODED_SIZE)
                    throw new IllegalArgumentException("Ảnh sau xử lý vượt " + MAX_ENCODED_SIZE + " byte. Hãy chọn ảnh nhỏ hơn.");
            }
            @Override public synchronized void write(int value) { checkSize(1); super.write(value); }
            @Override public synchronized void write(byte[] data, int offset, int length) {
                checkSize(length); super.write(data, offset, length);
            }
        };
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam options = writer.getDefaultWriteParam();
            IIOMetadata metadata = null;
            if ("jpeg".equals(format)) {
                options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                options.setCompressionQuality(JPEG_QUALITY);
                if (options.canWriteProgressive()) options.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), options);
                String treeName = "javax_imageio_jpeg_image_1.0";
                IIOMetadataNode tree = (IIOMetadataNode) metadata.getAsTree(treeName);
                org.w3c.dom.NodeList components = tree.getElementsByTagName("componentSpec");
                for (int index = 0; index < components.getLength(); index++) {
                    IIOMetadataNode component = (IIOMetadataNode) components.item(index);
                    component.setAttribute("HsamplingFactor", "1");
                    component.setAttribute("VsamplingFactor", "1");
                }
                metadata.setFromTree(treeName, tree);
            }
            // Use fresh metadata: retain image pixels without publishing source EXIF/GPS data.
            writer.write(null, new IIOImage(image, null, metadata), options);
            output.flush();
            return bytes.toByteArray();
        } finally { writer.dispose(); }
    }

    private IllegalArgumentException invalidImage() {
        return new IllegalArgumentException("Chỉ nhận ảnh JPG hoặc PNG hợp lệ. Vui lòng chọn lại ảnh.");
    }

    public void checkCapacity(int productId, List<Upload> uploads) throws IOException {
        if (!uploads.isEmpty() && list(productId).size() + uploads.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Mỗi sản phẩm tối đa " + MAX_IMAGES + " ảnh. Hãy giảm số ảnh tải thêm.");
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
